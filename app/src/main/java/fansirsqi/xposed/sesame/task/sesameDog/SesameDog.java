package fansirsqi.xposed.sesame.task.sesameDog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fansirsqi.xposed.sesame.hook.MtopSender;
import fansirsqi.xposed.sesame.hook.SesameDogAuth;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.StringUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

/**
 * 芝麻粒游乐记(芝麻狗)自动任务。
 *
 * <p>对每个已参与的城市游玩记录「前进」到当日上限; 一个城市刷满(接口返回
 * SESAME_DOG_DAILY_LIMIT)后自动切换下一个记录继续刷, 直到所有记录到上限或活动积分耗尽。
 *
 * <p>机制: 每次前进加站点进度、消耗活动积分(singleConsumePoints); 每日前进次数按
 * 游玩记录(playingId)独立计数, 切换记录可绕开当前记录的满额, 积分全局共用。
 *
 * <p>由 ModelTask 任务线程调用, 内部均为阻塞式 MTOP 调用。
 */
public class SesameDog extends ModelTask {

    private static final String TAG = SesameDog.class.getSimpleName();

    /**
     * 单城市前进次数的默认保护上限。真正的当日上限由服务端返回
     * SESAME_DOG_DAILY_LIMIT 决定(实测单记录 25/50 次), 这里留一倍余量;
     * 若刷到本地保护上限却未收到服务端上限, 调大「单城市前进次数上限」即可继续。
     */
    private static final int DEFAULT_FORWARD_LIMIT = 100;
    /** 保护上限的硬顶, 避免误填导致长时间连续请求 */
    private static final int HARD_FORWARD_LIMIT = 500;
    private static final int MAX_RECORDS_PER_ROUND = 40;
    private static final long SWITCH_INTERVAL_MS = 600L;
    /** 前进之间的随机间隔, 放慢避免风控 */
    private static final long FORWARD_INTERVAL_MIN_MS = 1200L;
    private static final long FORWARD_INTERVAL_MAX_MS = 2600L;

    /** 自动切换城市, 把每个记录都刷到当日上限 */
    private BooleanModelField autoSwitchCity;
    /** 单城市前进次数上限保护 */
    private IntegerModelField maxForwardPerCity;

    @Override
    public String getName() {
        return "芝麻粒游乐记";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public String getIcon() {
        return "SesameDog.png";
    }

    public SesameDog() {
        autoSwitchCity = new BooleanModelField("autoSwitchCity", "自动切换城市刷到上限", true);
        maxForwardPerCity = new IntegerModelField("maxForwardPerCity", "单城市前进次数上限(保护)",
                DEFAULT_FORWARD_LIMIT, 1, HARD_FORWARD_LIMIT);
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(autoSwitchCity);
        modelFields.addField(maxForwardPerCity);
        return modelFields;
    }

    @Override
    protected void runJava() {
        try {
            Log.other("芝麻粒游乐记: 开始 | " + MtopSender.stateText() + " " + SesameDogAuth.stateText());

            // 0) 路由探测: 活动改版/下线就别刷了
            MtopSender.Result pageroute = SesameDogRpcCall.pageroute();
            if (pageroute == null) {
                Log.other("芝麻粒游乐记⏸️ 活动探测失败: 无可用的授权 token");
                return;
            }
            if (!pageroute.ok) {
                Log.other("芝麻粒游乐记⏸️ 活动探测失败: " + pageroute.brief() + " | " + pageroute.note);
                return;
            }
            JSONObject routeBody = pageroute.body();
            String route = routeBody == null ? "" : routeBody.optString("routeRedirectType");
            if (!StringUtil.isEmpty(route) && !"DOG_HOME".equals(route)) {
                Log.other("芝麻粒游乐记⏸️ 活动路由=" + route + "(非 DOG_HOME), 本次不执行");
                return;
            }

            // 1) 首页: 当前城市/记录/节点状态/活动积分
            MtopSender.Result index = SesameDogRpcCall.index();
            if (index == null) {
                Log.other("芝麻粒游乐记⏸️ 未取到 alisports 授权 token, 本轮无法前进");
                return;
            }
            if (!index.ok) {
                Log.other("芝麻粒游乐记⏸️ 首页返回失败: " + index.brief() + " | " + index.note);
                return;
            }
            JSONObject indexBody = index.body();
            JSONObject playing = indexBody == null ? null : indexBody.optJSONObject("playingIndexResult");
            String nodeState = indexBody == null ? "" : indexBody.optString("nodeState");
            String playingId = playing == null ? "" : playing.optString("playingId");
            String cityName = "";
            JSONObject city = playing == null ? null : playing.optJSONObject("playingCity");
            if (city != null) {
                cityName = city.optString("cityName");
            }
            int points = pointsOf(indexBody);
            int unit = singleConsumeOf(indexBody);
            Log.other("芝麻粒游乐记: 当前城市=" + (StringUtil.isEmpty(cityName) ? "?" : cityName)
                    + " 记录=" + playingId + " 状态=" + nodeState
                    + " 活动积分=" + (points < 0 ? "?" : String.valueOf(points))
                    + " 单次消耗=" + unit);
            if (!"PLAYING".equals(nodeState)) {
                Log.other("芝麻粒游乐记⏸️ 当前节点状态=" + nodeState + ", 本次不前进");
                return;
            }
            if (points >= 0 && points < unit) {
                Log.other("芝麻粒游乐记⏸️ 活动积分不足(" + points + " < " + unit + "), 无法前进");
                return;
            }

            // 2) 参与记录: 每条记录独立计数, 从这里挑还能刷的城市
            List<Record> records = listRecords();
            if (records.isEmpty()) {
                if (StringUtil.isEmpty(playingId)) {
                    Log.other("芝麻粒游乐记⏸️ 没有可用的游玩记录");
                        return;
                }
                records.add(new Record(playingId, cityName, "", -1, ""));
            }
            logRecords(records, maxForwardPerCity.getValue());

            int ceiling = maxForwardPerCity.getValue();
            int currentIndex = indexOf(records, playingId);
            List<Record> order = rotateFrom(records, currentIndex);
            if (!autoSwitchCity.getValue()) {
                // 用户关掉自动切换: 只刷当前记录
                order = new ArrayList<>();
                if (currentIndex >= 0) {
                    order.add(records.get(currentIndex));
                } else if (!records.isEmpty()) {
                    order.add(records.get(0));
                }
                Log.other("芝麻粒游乐记: 自动切换城市已关闭, 只刷当前记录");
            }

            String currentId = playingId;
            int totalForward = 0;
            int switched = 0;
            int consecutiveFail = 0;
            for (Record rec : order) {
                int remain = rec.remain(ceiling);
                if (remain <= 0) {
                    Log.other("芝麻粒游乐记: 跳过 " + rec.label() + "(今日已前进 "
                            + Math.max(0, rec.todayCount) + " 次, 已达上限)");
                    continue;
                }
                if (!rec.playingId.equals(currentId)) {
                    MtopSender.Result change = SesameDogRpcCall.changeRecord(rec.playingId);
                    if (change == null || !change.ok) {
                        Log.other("芝麻粒游乐记⚠️ 切换到 " + rec.label() + " 失败: "
                                + (change == null ? "无响应" : change.brief()));
                        continue;
                    }
                    currentId = rec.playingId;
                    switched++;
                    Log.other("芝麻粒游乐记: 已切换城市记录 → " + rec.label() + "(待刷 " + remain + " 次)");
                    TimeUtil.sleep(SWITCH_INTERVAL_MS);
                }
                DrainResult drain = drain(rec, remain, unit);
                totalForward += drain.done;
                if (drain.stopByPoints) {
                    Log.other("芝麻粒游乐记: 活动积分耗尽, 本轮结束");
                    break;
                }
                if (drain.hardFail) {
                    if (++consecutiveFail >= 2) {
                        Log.other("芝麻粒游乐记⏸️ 接口连续失败, 本轮提前结束");
                        break;
                    }
                } else {
                    consecutiveFail = 0;
                }
            }

            // 3) 刷完后切回用户原来的当前记录, 免得活动页停在最后一个被刷的城市
            if (autoSwitchCity.getValue() && !StringUtil.isEmpty(playingId) && !playingId.equals(currentId)) {
                MtopSender.Result back = SesameDogRpcCall.changeRecord(playingId);
                if (back != null && back.ok) {
                    Log.other("芝麻粒游乐记: 已切回原当前记录 " + playingId);
                }
            }

            // 4) 汇总
            if (totalForward == 0 && switched == 0) {
                Log.other("芝麻粒游乐记: 所有城市记录均已达上限(或积分不足), 无需要前进的城市");
            } else {
                Log.other("芝麻粒游乐记: 本轮完成 | 切换城市 " + switched + " 个 | 前进 " + totalForward
                        + " 次 | 消耗活动积分约 " + totalForward * unit);
                Log.other("芝麻粒游乐记: 记录情况 " + remainText(records, ceiling));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "run err:", t);
        }
    }

    // ═══════════════════════════ 前进 ═══════════════════════════

    /** 把一条游玩记录刷到服务端上限(或安全上限)。 */
    private DrainResult drain(Record rec, int remain, int unit) {
        DrainResult out = new DrainResult();
        for (int i = 0; i < remain; i++) {
            MtopSender.Result r = SesameDogRpcCall.forward(rec.playingId);
            if (r == null) {
                out.hardFail = true;
                Log.other("芝麻粒游乐记⚠️ " + rec.label() + " 前进失败: 无响应(授权 token 不可用)");
                break;
            }
            if (r.ok) {
                out.done++;
                rec.drained++;
                String state = progressOf(r.body());
                if (!StringUtil.isEmpty(state)) {
                    out.lastState = state;
                }
            } else if (r.isDailyLimit()) {
                out.reachedLimit = true;
                rec.full = true;
                Log.other("芝麻粒游乐记: " + rec.label() + " 已刷到今日上限(本次前进 " + out.done + " 次)");
                break;
            } else if (r.isPointsNotEnough()) {
                out.stopByPoints = true;
                Log.other("芝麻粒游乐记: " + rec.label() + " 前进被拒(活动积分不足): " + r.brief());
                break;
            } else {
                out.hardFail = true;
                Log.other("芝麻粒游乐记⚠️ " + rec.label() + " 前进失败: " + r.brief() + " | " + r.note);
                break;
            }
            if (i + 1 < remain) {
                TimeUtil.sleep(randomDelay());
            }
        }
        if (out.done > 0) {
            Log.other("芝麻粒游乐记: " + rec.label() + " 前进 " + out.done + " 次(站点进度 +"
                    + out.done * 5 + ", 活动积分 -" + out.done * unit + ")"
                    + (StringUtil.isEmpty(out.lastState) ? "" : " | " + out.lastState));
        }
        if (!out.reachedLimit && !out.stopByPoints && !out.hardFail && out.done >= remain && remain > 0) {
            Log.other("芝麻粒游乐记⚠️ " + rec.label() + " 已达本地保护上限(" + remain
                    + " 次)但服务端未报上限; 该记录真实上限更大, 需要继续请调大「单城市前进次数上限」");
        }
        return out;
    }

    private List<Record> listRecords() {
        List<Record> list = new ArrayList<>();
        MtopSender.Result r = SesameDogRpcCall.listRecords();
        if (r == null || !r.ok) {
            Log.other("芝麻粒游乐记⚠️ 参与记录拉取失败: " + (r == null ? "无响应" : r.brief()));
            return list;
        }
        JSONObject body = r.body();
        JSONArray array = body == null ? null : body.optJSONArray("result");
        if (array == null) {
            return list;
        }
        String today = today();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = o.optString("playingId");
            if (StringUtil.isEmpty(id)) {
                continue;
            }
            JSONObject extend = o.optJSONObject("extend");
            int count = 0;
            String lastDate = "";
            if (extend != null) {
                count = toInt(extend.optString("todayForwardCount"), 0);
                lastDate = extend.optString("lastPlayingForwardDate");
            }
            if (!today.equals(lastDate)) {
                // 昨天及更早的次数不算今天
                count = 0;
            }
            list.add(new Record(id, o.optString("cityName"), o.optString("currentSiteName"), count, lastDate));
        }
        return list;
    }

    // ═══════════════════════════ 展示/工具 ═══════════════════════════

    private void logRecords(List<Record> records, int ceiling) {
        Log.other("芝麻粒游乐记: 参与记录 " + records.size() + " 条 | " + remainText(records, ceiling));
    }

    private static String remainText(List<Record> records, int ceiling) {
        StringBuilder sb = new StringBuilder();
        for (Record rec : records) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(rec.label()).append("(剩").append(rec.remainAfterRound(ceiling)).append(")");
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static String progressOf(JSONObject body) {
        if (body == null) {
            return "";
        }
        JSONObject p = body.optJSONObject("playingIndexResult");
        if (p != null) {
            body = p;
        }
        StringBuilder sb = new StringBuilder();
        String nodeState = body.optString("nodeState");
        if (!StringUtil.isEmpty(nodeState)) {
            sb.append("状态=").append(nodeState);
        }
        String progress = body.optString("currentSiteProgress");
        if (!StringUtil.isEmpty(progress)) {
            append(sb, "站点进度=" + progress);
        }
        String siteId = body.optString("currentSiteId");
        if (!StringUtil.isEmpty(siteId)) {
            append(sb, "站点=" + siteId);
        }
        String siteName = body.optString("currentSiteName");
        if (!StringUtil.isEmpty(siteName)) {
            append(sb, "站点名=" + siteName);
        }
        String forwardNode = body.optString("forwardNode");
        if (!StringUtil.isEmpty(forwardNode)) {
            append(sb, "节点=" + forwardNode);
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String text) {
        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append(text);
    }

    /** 活动积分: data.userPointsInfo.points(字符串)。 */
    private static int pointsOf(JSONObject indexBody) {
        if (indexBody == null) {
            return -1;
        }
        JSONObject info = indexBody.optJSONObject("userPointsInfo");
        if (info == null) {
            return -1;
        }
        return toInt(info.optString("points"), -1);
    }

    /** 单次前进消耗的活动积分: data.userPointsInfo.singleConsumePoints, 缺省 5。 */
    private static int singleConsumeOf(JSONObject indexBody) {
        if (indexBody == null) {
            return 5;
        }
        JSONObject info = indexBody.optJSONObject("userPointsInfo");
        if (info == null) {
            return 5;
        }
        int unit = toInt(info.optString("singleConsumePoints"), 5);
        return unit <= 0 ? 5 : unit;
    }

    private static int indexOf(List<Record> records, String playingId) {
        if (StringUtil.isEmpty(playingId)) {
            return -1;
        }
        for (int i = 0; i < records.size(); i++) {
            if (playingId.equals(records.get(i).playingId)) {
                return i;
            }
        }
        return -1;
    }

    /** 以当前记录为起点做环形排序, 保证先刷当前城市再依次切换。 */
    private static List<Record> rotateFrom(List<Record> records, int index) {
        List<Record> out = new ArrayList<>();
        if (records.isEmpty()) {
            return out;
        }
        int start = index >= 0 ? index : 0;
        for (int i = 0; i < records.size(); i++) {
            out.add(records.get((start + i) % records.size()));
        }
        if (out.size() > MAX_RECORDS_PER_ROUND) {
            return new ArrayList<>(out.subList(0, MAX_RECORDS_PER_ROUND));
        }
        return out;
    }

    private static long randomDelay() {
        long span = FORWARD_INTERVAL_MAX_MS - FORWARD_INTERVAL_MIN_MS;
        return FORWARD_INTERVAL_MIN_MS + (long) (Math.random() * span);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static int toInt(String text, int def) {
        if (StringUtil.isEmpty(text)) {
            return def;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (Throwable ignore) {
            return def;
        }
    }

    // ═══════════════════════════ 内部数据结构 ═══════════════════════════

    /** 一条「我参与过」的城市游玩记录。 */
    private static final class Record {

        final String playingId;
        final String cityName;
        final String siteName;
        final int todayCount;
        final String lastDate;
        /** 本轮已经前进的次数(用于汇总展示) */
        int drained;
        /** 本轮已收到服务端上限, 该记录今日不用再刷 */
        boolean full;

        Record(String playingId, String cityName, String siteName, int todayCount, String lastDate) {
            this.playingId = playingId;
            this.cityName = StringUtil.isEmpty(cityName) ? "?" : cityName;
            this.siteName = StringUtil.isEmpty(siteName) ? "?" : siteName;
            this.todayCount = todayCount;
            this.lastDate = lastDate == null ? "" : lastDate;
        }

        int remain(int ceiling) {
            int used = Math.max(0, todayCount);
            return Math.max(0, ceiling - used);
        }

        /** 本轮跑完后的剩余次数: 已收到服务端上限则为 0, 否则扣掉本轮已刷的次数。 */
        int remainAfterRound(int ceiling) {
            if (full) {
                return 0;
            }
            return Math.max(0, remain(ceiling) - drained);
        }

        String label() {
            return cityName + "·" + siteName + "(" + playingId + ")";
        }
    }

    /** 单条记录的前进结果。 */
    private static final class DrainResult {

        int done;
        boolean reachedLimit;
        boolean stopByPoints;
        boolean hardFail;
        String lastState = "";
    }
}
