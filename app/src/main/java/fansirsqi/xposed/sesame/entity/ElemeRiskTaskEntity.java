package fansirsqi.xposed.sesame.entity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import fansirsqi.xposed.sesame.util.Files;
import fansirsqi.xposed.sesame.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 饿了么风控与可跳过任务实体
 * 严禁无据硬编码，支持动态捕获风控拦截原因、时间戳并持久化到本地文件
 * 在 UI 设置页面中提供勾选列表，用户可查看各任务被拦截的具体原因与时间，并支持随时勾选/反选重新测试
 */
public class ElemeRiskTaskEntity extends MapperEntity {
    private static final String TAG = "ElemeRiskTaskEntity";
    private static final String CACHE_FILE_NAME = "eleme_risk_tasks.json";

    public static class RiskItem {
        public String id;
        public String title;
        public String reason;
        public String addedTime;

        public RiskItem(String id, String title, String reason, String addedTime) {
            this.id = id;
            this.title = title != null && !title.trim().isEmpty() ? title.trim() : ("任务_" + id);
            this.reason = reason != null && !reason.trim().isEmpty() ? reason.trim() : "风控拦截";
            this.addedTime = addedTime != null && !addedTime.trim().isEmpty() ? addedTime.trim() : "";
        }
    }

    private static final Map<String, RiskItem> KNOWN_RISK_TASKS = new LinkedHashMap<>();

    public String pureTitle;
    public String reason;
    public String addedTime;

    public ElemeRiskTaskEntity(String id, String name, String pureTitle, String reason, String addedTime) {
        this.id = id;
        this.name = name;
        this.pureTitle = pureTitle;
        this.reason = reason;
        this.addedTime = addedTime;
    }

    public ElemeRiskTaskEntity(String id, String name) {
        this(id, name, name, "", "");
    }

    static {
        loadFromLocalCache();
    }

    private static void loadFromLocalCache() {
        try {
            File file = new File(Files.CONFIG_DIR, CACHE_FILE_NAME);
            if (file.exists() && file.isFile() && file.length() > 0) {
                String content = Files.readFromFile(file);
                if (content != null && !content.trim().isEmpty()) {
                    JSONArray arr = new JSONArray(content.trim());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj == null) continue;
                        String id = obj.optString("id");
                        if (id.isEmpty()) continue;
                        String title = obj.optString("title");
                        String reason = obj.optString("reason");
                        String time = obj.optString("addedTime");
                        KNOWN_RISK_TASKS.put(id, new RiskItem(id, title, reason, time));
                    }
                }
            }
        } catch (Throwable t) {
            Log.error(TAG, "加载饿了么风控任务缓存失败: " + t.getMessage());
        }

        // 补齐已知基础典型风控任务（若本地未包含则补充）
        ensureDefaultItem("47780001", "浏览外卖品质好店", "405::行为受限(百川风控)");
        ensureDefaultItem("49020001", "在页面内点击3个店铺", "417::防作弊打点拦截");
        ensureDefaultItem("47774001", "浏览点击10次许愿星", "405::行为受限(百川风控)");
        ensureDefaultItem("48596001", "去闲鱼看视频领奖励", "协议风控/不支持RPC");
    }

    private static void ensureDefaultItem(String id, String title, String reason) {
        if (!KNOWN_RISK_TASKS.containsKey(id)) {
            KNOWN_RISK_TASKS.put(id, new RiskItem(id, title, reason, "预置"));
        }
    }

    private static synchronized void saveToLocalCache() {
        try {
            JSONArray arr = new JSONArray();
            for (RiskItem item : KNOWN_RISK_TASKS.values()) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("reason", item.reason);
                obj.put("addedTime", item.addedTime);
                arr.put(obj);
            }
            File file = new File(Files.CONFIG_DIR, CACHE_FILE_NAME);
            Files.write2FileAtomic(arr.toString(2), file);
        } catch (Throwable t) {
            Log.error(TAG, "持久化饿了么风控任务缓存失败: " + t.getMessage());
        }
    }

    /**
     * 动态记录命中风控的任务ID、名称与原因，并持久化到本地且自动加入跳过集合
     */
    public static synchronized void recordRiskTask(String id, String name, String reason) {
        if (id == null || id.trim().isEmpty()) return;
        String cleanId = id.trim();
        String cleanTitle = (name != null && !name.trim().isEmpty()) ? name.trim() : ("任务_" + cleanId);
        String cleanReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "405::行为受限";
        String timeStr = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());

        RiskItem existing = KNOWN_RISK_TASKS.get(cleanId);
        if (existing == null || !existing.reason.equals(cleanReason)) {
            KNOWN_RISK_TASKS.put(cleanId, new RiskItem(cleanId, cleanTitle, cleanReason, timeStr));
            saveToLocalCache();
        }

        try {
            Set<String> selected = fansirsqi.xposed.sesame.task.otherTask2.eleme.ElemeTask.getElemeRiskTasks().getValue();
            if (selected != null && selected.add(cleanId)) {
                fansirsqi.xposed.sesame.data.Config.save(fansirsqi.xposed.sesame.util.maps.UserMap.currentUid, true);
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized void recordRiskTask(String id, String name) {
        recordRiskTask(id, name, "行为受限");
    }

    /**
     * 供 UI 设置页 SelectModelField 调用的选项获取方法
     */
    public static synchronized List<ElemeRiskTaskEntity> getList() {
        List<ElemeRiskTaskEntity> list = new ArrayList<>();
        for (RiskItem item : KNOWN_RISK_TASKS.values()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(item.id).append("] ").append(item.title);
            if (item.reason != null && !item.reason.isEmpty()) {
                sb.append(" (").append(item.reason);
                if (item.addedTime != null && !item.addedTime.isEmpty() && !item.addedTime.equals("预置")) {
                    sb.append(" ").append(item.addedTime);
                }
                sb.append(")");
            }
            list.add(new ElemeRiskTaskEntity(item.id, sb.toString(), item.title, item.reason, item.addedTime));
        }
        return list;
    }

    public static synchronized Map<String, String> getKnownRiskTasks() {
        Map<String, String> map = new LinkedHashMap<>();
        for (RiskItem item : KNOWN_RISK_TASKS.values()) {
            map.put(item.id, item.title + (item.reason.isEmpty() ? "" : " [" + item.reason + "]"));
        }
        return map;
    }
}
