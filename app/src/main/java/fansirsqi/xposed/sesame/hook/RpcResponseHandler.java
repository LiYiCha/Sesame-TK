package fansirsqi.xposed.sesame.hook;

import fansirsqi.xposed.sesame.data.Config;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.task.otherTask.OtherTask;
import fansirsqi.xposed.sesame.util.maps.UserMap;

public class RpcResponseHandler {

    private static final String TAG = "RpcResponseHandler";

    /**
     * 处理指定RPC方法的响应数据并提取关键信息
     *
     * @param method 方法名
     * @param Params 原始JSON字符串
     */
    public static void handle(String method, String Params) {
        if (Params == null || Params.isEmpty() || "null".equalsIgnoreCase(Params)) {
            Log.runtime(TAG + ": Params为空或无效");
            return;
        }

        try {
            switch (method) {
                case "com.alipay.antfishpond.fishpondAngle":
                    extractRiskToken(Params);
                    break;
                // 其他接口的处理逻辑
                default:
                    //Log.runtime(TAG + ": 未定义处理逻辑的RPC方法: " + method);
                    break;
            }

        } catch (Exception e) {
            Log.runtime(TAG + ": JSON解析失败 Params=" + Params);
            Log.printStackTrace(e);
        }
    }

    /**
     * 提取 fishpondAngle 接口中的 riskToken
     */
    private static void extractRiskToken(String rawJson) {
        // 直接操作原始JSON字符串
        final String targetKey = "\"riskToken\":\"";
        int tokenPos = rawJson.indexOf(targetKey);
        if (tokenPos == -1) return;

        int valueStart = tokenPos + targetKey.length();
        int valueEnd = findClosingQuote(rawJson, valueStart);

        if (valueEnd > valueStart) {
            String riskToken = rawJson.substring(valueStart, valueEnd);
            OtherTask.getFishpondToken().setValue(riskToken);
            saveConfigWithLog("fishpondToken", riskToken);
        }
    }

    // 智能查找闭合引号的工具方法
    private static int findClosingQuote(String json, int start) {
        int end = start;
        boolean escaped = false;

        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"' && !escaped) {
                return end;
            }
            escaped = (c == '\\' && !escaped);
            end++;
        }
        return -1;
    }

    private static void saveConfigWithLog(String fieldName, String value) {
        String userId = UserMap.getCurrentUid();
            if (Config.save(userId, false)) {
                Log.runtime("✅ 配置 [" + fieldName + "] 已保存: " + value);
            } else {
                Log.runtime("❌ 配置 [" + fieldName + "] 保存失败: " + value);
            }
    }
}
