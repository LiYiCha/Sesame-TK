package fansirsqi.xposed.sesame.entity;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import fansirsqi.xposed.sesame.util.Files;
import fansirsqi.xposed.sesame.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 饿了么幸运星兑换商城商品实体
 * 完全动态来源于接口 mtop.alsc.interact.playapp.reward.right.mallrecall
 * 严禁任何硬编码伪造数据，支持秒杀兑换时间表、规则说明与本地自愈缓存
 */
public class ElemeMallEntity extends MapperEntity {
    private static final String TAG = "ElemeMallEntity";
    private static final Map<String, ElemeMallEntity> MALL_ITEMS = new LinkedHashMap<>();

    public String pureTitle;
    public int consumeAmount;
    public String actId;
    public String exchangeCollectionId;
    public String actCode;
    public String exchangeStatus;
    public int gapAmount;
    public String timeDesc;
    public String ruleDesc;
    public String stockDesc;

    public ElemeMallEntity(String id, String name, String pureTitle, int consumeAmount, String actId, String exchangeCollectionId, String actCode, String exchangeStatus, int gapAmount, String timeDesc, String ruleDesc, String stockDesc) {
        this.id = id;
        this.name = name;
        this.pureTitle = pureTitle != null && !pureTitle.isEmpty() ? pureTitle : name;
        this.consumeAmount = consumeAmount;
        this.actId = actId != null ? actId : "";
        this.exchangeCollectionId = exchangeCollectionId != null ? exchangeCollectionId : "";
        this.actCode = actCode != null ? actCode : "";
        this.exchangeStatus = exchangeStatus != null ? exchangeStatus : "";
        this.gapAmount = gapAmount;
        this.timeDesc = timeDesc != null ? timeDesc : "";
        this.ruleDesc = ruleDesc != null ? ruleDesc : "";
        this.stockDesc = stockDesc != null ? stockDesc : "";
    }

    public ElemeMallEntity(String id, String name, int consumeAmount, String actId, String exchangeCollectionId, String actCode, String exchangeStatus, int gapAmount, String timeDesc, String ruleDesc, String stockDesc) {
        this(id, name, name, consumeAmount, actId, exchangeCollectionId, actCode, exchangeStatus, gapAmount, timeDesc, ruleDesc, stockDesc);
    }

    static {
        // 绝不硬编码任何虚构商品，严格从本地历史缓存加载真实拉取过的商品
        loadFromLocalCache();
    }

    public static synchronized void recordItem(String id, String name, String pureTitle, int cost, String actId, String colId, String actCode, String status, int gap, String timeDesc, String ruleDesc, String stockDesc) {
        if (id != null && !id.trim().isEmpty()) {
            MALL_ITEMS.put(id.trim(), new ElemeMallEntity(id.trim(), name, pureTitle, cost, actId, colId, actCode, status, gap, timeDesc, ruleDesc, stockDesc));
        }
    }

    public static synchronized void recordItem(String id, String name, int cost, String actId, String colId, String actCode, String status, int gap, String timeDesc, String ruleDesc, String stockDesc) {
        recordItem(id, name, name, cost, actId, colId, actCode, status, gap, timeDesc, ruleDesc, stockDesc);
    }

    /**
     * 动态从商城查询接口（mallrecall）更新商品列表，并持久化到本地文件
     */
    public static synchronized void updateFromMallResponse(JSONObject resp) {
        if (resp == null) return;
        JSONObject data = resp.optJSONObject("data");
        if (data == null) data = resp;
        JSONArray list = data.optJSONArray("list");
        if (list == null || list.length() == 0) return;

        MALL_ITEMS.clear(); // 清理旧缓存，确保列表完全反映接口最新拉取结果
        for (int i = 0; i < list.length(); i++) {
            JSONObject it = list.optJSONObject(i);
            if (it == null) continue;
            JSONObject ex = it.optJSONObject("exchangeInfo");
            JSONObject mat = it.optJSONObject("materialInfo");
            JSONObject biz = it.optJSONObject("bizExtInfo");

            String exchangeId = it.optString("exchangeId");
            if (exchangeId.isEmpty()) {
                exchangeId = String.valueOf(it.optLong("exchangeId"));
            }
            if (exchangeId.isEmpty() || exchangeId.equals("0")) {
                exchangeId = String.valueOf(i + 1);
            }
            int cost = ex != null ? ex.optInt("consumeAmount", 0) : 0;
            int gap = ex != null ? ex.optInt("gapAmount", 0) : 0;
            String status = ex != null ? ex.optString("exchangeStatus", "") : "";

            String title = mat != null ? mat.optString("title") : "";
            if (title.isEmpty() && biz != null) {
                title = biz.optString("exchangeIntroduction", "");
            }
            if (title.isEmpty()) {
                title = "商品_" + exchangeId;
            }

            // 提取秒杀兑换时间表、排期与规则
            String timeDesc = "";
            if (ex != null) {
                timeDesc = ex.optString("exchangeTimeDesc");
                if (timeDesc.isEmpty()) timeDesc = ex.optString("timeDesc");
                if (timeDesc.isEmpty() && ex.has("startTime") && ex.has("endTime")) {
                    timeDesc = ex.optString("startTime") + " ~ " + ex.optString("endTime");
                }
            }
            if (timeDesc.isEmpty() && biz != null) {
                timeDesc = biz.optString("seckillTimeDesc");
                if (timeDesc.isEmpty()) timeDesc = biz.optString("countdownDesc");
            }
            if (timeDesc.isEmpty()) {
                timeDesc = "每日按时补货 / 随时可兑";
            }

            // 提取使用门槛与说明
            String ruleDesc = "";
            if (biz != null) {
                ruleDesc = biz.optString("exchangeIntroduction");
                if (ruleDesc.isEmpty()) ruleDesc = biz.optString("useRule");
                if (ruleDesc.isEmpty()) ruleDesc = biz.optString("description");
            }
            if (ruleDesc.isEmpty() && mat != null) {
                ruleDesc = mat.optString("subTitle");
            }

            // 提取库存限量说明
            String stockDesc = "";
            if (ex != null) {
                int remain = ex.optInt("remainCount", -1);
                if (remain >= 0) {
                    stockDesc = "剩余库存: " + remain + " 件";
                } else {
                    stockDesc = ex.optString("limitDesc");
                }
            }

            String costTag = cost > 0 ? " [" + cost + "星]" : "";
            String statusTag = "";
            if (gap > 0) {
                statusTag = " (差" + gap + "星)";
            } else if ("PROPERTY_NOT_ENOUGH".equals(status)) {
                statusTag = " (星不足)";
            } else if ("SOLD_OUT".equals(status)) {
                statusTag = " (已抢光)";
            } else if ("ONLINE".equals(status) || "CAN_EXCHANGE".equals(status)) {
                statusTag = " (可兑)";
            }
            String displayName = title + costTag + statusTag;

            String actId = it.optString("exchangeActId");
            if (actId.isEmpty()) actId = it.optString("actId");

            String colId = it.optString("exchangeCollectionId");
            if (colId.isEmpty()) colId = it.optString("collectionId");

            String actCode = it.optString("actCode");

            recordItem(exchangeId, displayName, title, cost, actId, colId, actCode, status, gap, timeDesc, ruleDesc, stockDesc);
        }

        // 写入本地持久化缓存
        saveToLocalCache();
    }

    /**
     * 生成结构化且美观的单商品完整详情文本（供点击商品时弹窗展示）
     */
    public String getFullDetailText() {
        StringBuilder sb = new StringBuilder();
        String displayTitle = (this.pureTitle != null && !this.pureTitle.isEmpty()) ? this.pureTitle : this.name;
        sb.append("【商品名称】: ").append(displayTitle).append("\n\n");
        sb.append("• 所需幸运星: ").append(this.consumeAmount).append(" 星\n");
        String statusText = "可兑换";
        if (this.gapAmount > 0) {
            statusText = "星数不足 (还差 " + this.gapAmount + " 星)";
        } else if ("SOLD_OUT".equals(this.exchangeStatus)) {
            statusText = "今日已抢光";
        } else if ("PROPERTY_NOT_ENOUGH".equals(this.exchangeStatus)) {
            statusText = "星数不足";
        } else if (!this.exchangeStatus.isEmpty()) {
            statusText = this.exchangeStatus;
        }
        sb.append("• 兑换状态: ").append(statusText).append("\n");
        if (!this.stockDesc.isEmpty()) {
            sb.append("• 库存与限量: ").append(this.stockDesc).append("\n");
        }
        sb.append("\n【秒杀兑换时间表】:\n• ").append(this.timeDesc.isEmpty() ? "每日按时补货 / 随时可兑" : this.timeDesc).append("\n\n");
        if (!this.ruleDesc.isEmpty()) {
            sb.append("【使用门槛与规则】:\n• ").append(this.ruleDesc).append("\n\n");
        }
        sb.append("【接口标识】:\n• exchangeId: ").append(this.id);
        if (!this.actId.isEmpty()) sb.append("\n• actId: ").append(this.actId);
        if (!this.actCode.isEmpty()) sb.append("\n• actCode: ").append(this.actCode);
        return sb.toString();
    }

    private static File getCacheFile() {
        try {
            return new File(Files.CONFIG_DIR, "eleme_mall_cache.json");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void saveToLocalCache() {
        try {
            File f = getCacheFile();
            if (f == null) return;
            JSONArray arr = new JSONArray();
            for (ElemeMallEntity item : MALL_ITEMS.values()) {
                JSONObject jo = new JSONObject();
                jo.put("id", item.id);
                jo.put("name", item.name);
                jo.put("pureTitle", item.pureTitle);
                jo.put("cost", item.consumeAmount);
                jo.put("actId", item.actId);
                jo.put("colId", item.exchangeCollectionId);
                jo.put("actCode", item.actCode);
                jo.put("status", item.exchangeStatus);
                jo.put("gap", item.gapAmount);
                jo.put("timeDesc", item.timeDesc);
                jo.put("ruleDesc", item.ruleDesc);
                jo.put("stockDesc", item.stockDesc);
                arr.put(jo);
            }
            Files.write2File(arr.toString(), f);
        } catch (Throwable t) {
            Log.error(TAG, "saveToLocalCache error: " + t.getMessage());
        }
    }

    private static void loadFromLocalCache() {
        try {
            File f = getCacheFile();
            if (f == null || !f.exists()) return;
            String content = Files.readFromFile(f);
            if (content == null || content.trim().isEmpty()) return;
            JSONArray arr = new JSONArray(content.trim());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject jo = arr.optJSONObject(i);
                if (jo == null) continue;
                String id = jo.optString("id");
                String name = jo.optString("name");
                String pureTitle = jo.optString("pureTitle", name);
                int cost = jo.optInt("cost", 0);
                String actId = jo.optString("actId");
                String colId = jo.optString("colId");
                String actCode = jo.optString("actCode");
                String status = jo.optString("status");
                int gap = jo.optInt("gap", 0);
                String timeDesc = jo.optString("timeDesc");
                String ruleDesc = jo.optString("ruleDesc");
                String stockDesc = jo.optString("stockDesc");
                if (!id.isEmpty() && !name.isEmpty()) {
                    recordItem(id, name, pureTitle, cost, actId, colId, actCode, status, gap, timeDesc, ruleDesc, stockDesc);
                }
            }
        } catch (Throwable t) {
            Log.error(TAG, "loadFromLocalCache error: " + t.getMessage());
        }
    }

    /**
     * 供 UI 设置页 SelectModelField 调用的选项获取方法
     */
    public static synchronized List<ElemeMallEntity> getList() {
        if (MALL_ITEMS.isEmpty()) {
            // 如果尚未从接口拉取且无本地缓存，给出唯一清晰的动态提示项
            List<ElemeMallEntity> emptyTip = new ArrayList<>();
            emptyTip.add(new ElemeMallEntity("0", "【暂未拉取商品，请点击【查看/刷新兑换商城】获取最新真实列表】", 0, "", "", "", "", 0, "", "", ""));
            return emptyTip;
        }
        return new ArrayList<>(MALL_ITEMS.values());
    }

    public static synchronized ElemeMallEntity getItem(String id) {
        return MALL_ITEMS.get(id);
    }
}
