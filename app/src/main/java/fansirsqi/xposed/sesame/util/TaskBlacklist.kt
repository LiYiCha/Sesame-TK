package fansirsqi.xposed.sesame.util

import com.fasterxml.jackson.core.type.TypeReference
import fansirsqi.xposed.sesame.newutil.defaultBlacklist

/**
 * 通用任务黑名单管理器
 * 支持永久黑名单（协议风控/事件规则任务永久拦截）与当日黑名单（频率超限任务次日重试）
 */
object TaskBlacklist {
    private const val TAG = "TaskBlacklist"
    private const val PERMANENT_KEY = "task_blacklist_permanent"
    private const val DAILY_KEY = "task_blacklist_daily"

    private fun today(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date())

    /**
     * 读取永久黑名单集合
     */
    private fun loadPermanent(): MutableSet<String> {
        return try {
            val raw = DataStore.getOrCreate(PERMANENT_KEY, object : TypeReference<Any>() {})
            when (raw) {
                is Set<*> -> raw.map { it.toString() }.toMutableSet()
                is List<*> -> raw.map { it.toString() }.toMutableSet()
                else -> mutableSetOf()
            }
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    private fun savePermanent(entries: Set<String>) {
        try {
            DataStore.put(PERMANENT_KEY, entries)
        } catch (e: Exception) {
            Log.error(TAG, "保存永久黑名单失败: ${e.message}")
        }
    }

    /**
     * 读取当日黑名单集合（跨天自动清理临时限频任务）
     */
    private fun loadDaily(): MutableSet<String> {
        return try {
            val raw = DataStore.getOrCreate(DAILY_KEY, object : TypeReference<Any>() {})
            if (raw is Map<*, *>) {
                val date = raw["date"]?.toString() ?: return mutableSetOf()
                if (date != today()) {
                    return mutableSetOf()
                }
                when (val entries = raw["entries"]) {
                    is Set<*> -> entries.map { it.toString() }.toMutableSet()
                    is List<*> -> entries.map { it.toString() }.toMutableSet()
                    else -> mutableSetOf()
                }
            } else {
                mutableSetOf()
            }
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    private fun saveDaily(entries: Set<String>) {
        try {
            DataStore.put(DAILY_KEY, mapOf("date" to today(), "entries" to entries))
        } catch (e: Exception) {
            Log.error(TAG, "保存当日黑名单失败: ${e.message}")
        }
    }

    /**
     * 获取全量合并黑名单（默认预置 + 永久黑名单 + 当日黑名单）
     */
    @JvmStatic
    fun getBlacklist(): Set<String> {
        return try {
            defaultBlacklist + loadPermanent() + loadDaily()
        } catch (e: Exception) {
            defaultBlacklist
        }
    }

    /**
     * 判断任务是否在黑名单中
     * @param taskInfo 任务ID或任务标题
     */
    @JvmStatic
    fun isTaskInBlacklist(taskInfo: String?): Boolean {
        if (taskInfo.isNullOrBlank()) return false
        val cleanInfo = taskInfo.trim()

        val allBlacklist = getBlacklist()
        for (item in allBlacklist) {
            if (item.isBlank()) continue
            if (cleanInfo.equals(item, ignoreCase = true)) return true

            // 中文字符串双向包含判定
            val itemHasChinese = item.any { it in '\u4e00'..'\u9fa5' }
            val infoHasChinese = cleanInfo.any { it in '\u4e00'..'\u9fa5' }

            if (itemHasChinese && infoHasChinese) {
                if (cleanInfo.contains(item) || item.contains(cleanInfo)) return true
            } else if (itemHasChinese) {
                if (cleanInfo.contains(item)) return true
            } else {
                // 纯英文/数字（如任务ID匹配）
                if (cleanInfo.contains(item, ignoreCase = true) || item.contains(cleanInfo, ignoreCase = true)) return true
            }
        }
        return false
    }

    /**
     * 手动添加到永久黑名单
     */
    @JvmStatic
    @JvmOverloads
    fun addToBlacklist(taskId: String, taskTitle: String = "") {
        if (taskId.isBlank() && taskTitle.isBlank()) return
        val current = loadPermanent()
        var changed = false
        if (taskId.isNotBlank() && current.add(taskId.trim())) changed = true
        if (taskTitle.isNotBlank() && current.add(taskTitle.trim())) changed = true
        if (changed) {
            savePermanent(current)
            val info = if (taskTitle.isNotBlank()) "$taskId - $taskTitle" else taskId
            Log.runtime(TAG, "任务[$info]已加入永久黑名单")
        }
    }

    /**
     * 从黑名单中移除
     */
    @JvmStatic
    @JvmOverloads
    fun removeFromBlacklist(taskId: String, taskTitle: String = "") {
        val perm = loadPermanent()
        val daily = loadDaily()
        var changed = false
        if (taskId.isNotBlank()) {
            if (perm.remove(taskId.trim())) changed = true
            if (daily.remove(taskId.trim())) changed = true
        }
        if (taskTitle.isNotBlank()) {
            if (perm.remove(taskTitle.trim())) changed = true
            if (daily.remove(taskTitle.trim())) changed = true
        }
        if (changed) {
            savePermanent(perm)
            saveDaily(daily)
            val info = if (taskTitle.isNotBlank()) "$taskId - $taskTitle" else taskId
            Log.runtime(TAG, "任务[$info]已从黑名单移除")
        }
    }

    /**
     * 清空黑名单
     */
    @JvmStatic
    fun clearBlacklist() {
        savePermanent(emptySet())
        saveDaily(emptySet())
        Log.runtime(TAG, "已清空任务黑名单")
    }

    /**
     * 根据执行错误特征自动加入黑名单
     */
    @JvmStatic
    @JvmOverloads
    fun autoAddToBlacklist(taskId: String, taskTitle: String = "", errorCode: String, errorMsg: String = "") {
        if (taskId.isBlank() && taskTitle.isBlank()) return

        // 1. 不支持 RPC 调用（协议风控）
        val isUnsupportedRpc = errorCode == "400000040" ||
                errorMsg.contains("不支持rpc调用") ||
                errorMsg.contains("不支持rpc")

        // 2. promoprod 促销平台事件规则任务（如下单、跑腿、搬家等不可通过 trigger 完成的任务）
        val isPromoProdEvent = errorCode == "10000005" ||
                errorMsg.contains("promoprod不允许完成事件规则任务") ||
                errorMsg.contains("不允许完成事件规则任务")

        // 3. 报名记录不存在或已过期
        val isSignupRecordMissing = errorCode == "10002901" ||
                errorMsg.contains("任务报名记录不存在或已过期") ||
                errorMsg.contains("任务报名记录不存在")

        // 4. 事件模板不存在或参数非法
        val isTemplateNotExist = errorCode == "PROMISE_TEMPLATE_NOT_EXIST" ||
                errorMsg.contains("事件模板不存在") ||
                errorMsg.contains("模板不存在") ||
                errorMsg.contains("生活记录模板不存在")
        val isInvalidArgument = errorCode == "ILLEGAL_ARGUMENT" ||
                errorCode == "TASK_ID_INVALID" ||
                errorMsg.contains("参数错误") ||
                errorMsg.contains("不是有效的入参")

        // 5. 安全策略或无配置
        val isSecurityReject = errorCode == "400000001" ||
                errorMsg.contains("安全策略拦截") ||
                errorMsg.contains("任务全局配置不存在")

        // 6. 频率限制（当日限频，次日可重试）
        val isRateLimit = errorCode == "OP_REPEAT_CHECK" || errorMsg.contains("操作太频繁")

        val isPermanent = isUnsupportedRpc || isPromoProdEvent || isSignupRecordMissing || isTemplateNotExist || isInvalidArgument || isSecurityReject
        val shouldAdd = isPermanent || isRateLimit

        if (shouldAdd) {
            val cleanId = taskId.trim()
            val cleanTitle = taskTitle.trim()

            val reason = when {
                isUnsupportedRpc -> "接口不支持RPC调用"
                isPromoProdEvent -> "不允许RPC完成事件规则任务(需真实交易)"
                isSignupRecordMissing -> "任务报名记录不存在或已过期"
                isTemplateNotExist -> "事件模板不存在"
                isInvalidArgument -> "任务参数无效或已下架"
                isSecurityReject -> "安全策略拦截"
                isRateLimit -> "操作太频繁(当日限频)"
                else -> "执行异常: $errorCode - $errorMsg"
            }

            if (isPermanent) {
                val perm = loadPermanent()
                var updated = false
                if (cleanId.isNotBlank() && perm.add(cleanId)) updated = true
                if (cleanTitle.isNotBlank() && perm.add(cleanTitle)) updated = true
                if (updated) savePermanent(perm)
            } else {
                val daily = loadDaily()
                var updated = false
                if (cleanId.isNotBlank() && daily.add(cleanId)) updated = true
                if (cleanTitle.isNotBlank() && daily.add(cleanTitle)) updated = true
                if (updated) saveDaily(daily)
            }

            val display = if (cleanTitle.isNotBlank()) "$cleanId - $cleanTitle" else cleanId
            Log.runtime(TAG, "🛡️ 任务[$display]因【$reason】已自动加入黑名单，后续跳过执行")
        }
    }
}
