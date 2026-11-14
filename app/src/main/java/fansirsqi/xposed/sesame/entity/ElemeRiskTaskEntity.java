package fansirsqi.xposed.sesame.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 饿了么风控与可跳过任务实体
 * 用于在 UI 设置页面显示多选列表，支持用户勾选跳过或取消勾选重新测试
 */
public class ElemeRiskTaskEntity extends MapperEntity {
    private static final Map<String, String> KNOWN_RISK_TASKS = new LinkedHashMap<>();

    static {
        // 预置已知的易被阿里百川风控或不支持自动化推进的典型任务
        KNOWN_RISK_TASKS.put("47780001", "浏览点击10次扭蛋券");
        KNOWN_RISK_TASKS.put("49020001", "今日累计任务奖励");
        KNOWN_RISK_TASKS.put("47774001", "浏览点击10次许愿星");
        KNOWN_RISK_TASKS.put("48596001", "去闲鱼看视频领奖励");
    }

    public ElemeRiskTaskEntity(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * 动态记录命中风控的任务ID与名称
     */
    public static synchronized void recordRiskTask(String id, String name) {
        if (id != null && !id.trim().isEmpty()) {
            String cleanId = id.trim();
            String cleanName = (name != null && !name.trim().isEmpty()) ? name.trim() : ("任务_" + cleanId);
            KNOWN_RISK_TASKS.put(cleanId, cleanName);
        }
    }

    /**
     * 供 UI 设置页 SelectModelField 调用的选项获取方法
     */
    public static synchronized List<ElemeRiskTaskEntity> getList() {
        List<ElemeRiskTaskEntity> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : KNOWN_RISK_TASKS.entrySet()) {
            list.add(new ElemeRiskTaskEntity(entry.getKey(), "[" + entry.getKey() + "] " + entry.getValue()));
        }
        return list;
    }

    public static synchronized Map<String, String> getKnownRiskTasks() {
        return new LinkedHashMap<>(KNOWN_RISK_TASKS);
    }
}
