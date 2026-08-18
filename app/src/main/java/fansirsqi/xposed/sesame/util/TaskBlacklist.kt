package fansirsqi.xposed.sesame.util

import com.fasterxml.jackson.core.type.TypeReference
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.newutil.defaultBlacklist

/**
 * 通用任务黑名单管理器
 * 使用DataStore持久化存储黑名单数据
 */
object TaskBlacklist {
    private const val TAG = "TaskBlacklist"
    private const val BLACKLIST_KEY = "task_blacklist"

    /**
     * 获取黑名单列表
     * @return 黑名单任务集合
     */
    @JvmStatic
    fun getBlacklist(): Set<String> {
        return try {
            val storedBlacklist = DataStore.getOrCreate(BLACKLIST_KEY, object : TypeReference<Set<String>>() {})
            // 合并存储的黑名单和默认黑名单
            (storedBlacklist + defaultBlacklist).toSet()
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "获取黑名单失败，使用默认黑名单", e)
            defaultBlacklist
        }
    }



    /**
     * 保存黑名单列表
     * @param blacklist 要保存的黑名单集合
     */
    private fun saveBlacklist(blacklist: Set<String>) {
        try {
            DataStore.put(BLACKLIST_KEY, blacklist)
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "保存黑名单失败", e)
        }
    }



    /**
     * 检查任务是否在黑名单中（精确匹配逻辑）
     * @param taskInfo 任务信息（可以是任务ID、任务标题或组合信息）
     * @return true表示在黑名单中，应该跳过
     */
    @JvmStatic
    fun isTaskInBlacklist(taskInfo: String?): Boolean {
        if (taskInfo.isNullOrBlank()) return false

        val blacklist = getBlacklist()
        return blacklist.any { item ->
            if (item.isBlank()) return@any false

            // 完全匹配（最精确）
            if (taskInfo == item) return@any true

            // 区分处理中文关键词和纯英文的匹配模式。
            val itemHasChinese = item.any { it in '\u4e00'..'\u9fa5' }

            if (itemHasChinese) {
                // 包含中文的项维持双向模糊匹配逻辑
                taskInfo.contains(item) || item.contains(taskInfo)
            } else {
                /* 纯英文/数字/符号项使用单向模糊匹配逻辑；防止黑名单中"TAOBAO"这类比较简短、通用的字段匹配到任务
                    "TAOBAO_tab2gzy" ，导致不是在黑名单中的任务被跳过
                 */
                item.contains(taskInfo)
            }
        }
    }

    /**
     * 添加任务到黑名单
     * @param taskId 要添加的任务ID
     * @param taskTitle 任务标题（可选，用于模糊匹配）
     */
    @JvmStatic
    @JvmOverloads
    fun addToBlacklist(taskId: String, taskTitle: String = "") {
        if (taskId.isBlank()) return
        // 如果提供了任务标题，则将ID和标题组合后添加，支持模糊匹配
        val blacklistItem = if (taskTitle.isNotBlank()) "$taskId$taskTitle" else taskId
        val currentBlacklist = getBlacklist().toMutableSet()
        if (currentBlacklist.add(blacklistItem)) {
            saveBlacklist(currentBlacklist)
        }
    }

    /**
     * 从黑名单中移除任务
     * @param taskId 要移除的任务ID
     * @param taskTitle 任务标题（可选，用于模糊匹配）
     */
    @JvmStatic
    @JvmOverloads
    fun removeFromBlacklist(taskId: String, taskTitle: String = "") {
        if (taskId.isBlank()) return

        // 如果提供了任务标题，则将ID和标题组合后移除，支持模糊匹配
        val blacklistItem = if (taskTitle.isNotBlank()) "$taskId$taskTitle" else taskId

        val currentBlacklist = getBlacklist().toMutableSet()
        if (currentBlacklist.remove(blacklistItem)) {
            saveBlacklist(currentBlacklist)
            val displayInfo = if (taskTitle.isNotBlank()) "$taskId - $taskTitle" else taskId
            Log.runtime(TAG, "任务[$displayInfo]已从黑名单移除")
        }
    }

    /**
     * 清空黑名单
     */
    @JvmStatic
    fun clearBlacklist() {
        try {
            saveBlacklist(emptySet())
            Log.runtime(TAG, "黑名单已清空")
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "清空黑名单失败", e)
        }
    }

    /**
     * 根据错误码自动添加任务到黑名单
     * 当任务执行失败时，如果错误码属于预定义的无法恢复的错误类型，
     * 系统会自动将该任务加入黑名单，避免重复执行失败的任务
     *
     * @param taskId 任务ID，用于标识具体任务
     * @param taskTitle 任务标题（可选），用于显示和模糊匹配
     * @param errorCode 错误码，用于判断是否需要自动加入黑名单
     * @param errorMsg 错误详情描述，用于模糊分析
     */
    @JvmStatic
    @JvmOverloads
    fun autoAddToBlacklist(taskId: String, taskTitle: String = "", errorCode: String, errorMsg: String = "") {
        // 参数校验：如果任务ID为空，直接返回
        if (taskId.isBlank()) return
        
        // 分析错误码及错误详情
        val isUnsupportedRpc = errorCode == "400000040" || errorMsg.contains("不支持rpc调用") || errorMsg.contains("不支持")
        val isInvalidArgument = errorCode == "ILLEGAL_ARGUMENT" || errorMsg.contains("不是有效入参") || errorMsg.contains("不是有效的入参")
        val isTemplateNotExist = errorCode == "PROMISE_TEMPLATE_NOT_EXIST" || errorMsg.contains("生活记录模板不存在")
        val isPromoProdError = errorCode == "10000005" || errorMsg.contains("promoprod不允许完成事件规则任务")
        val isNoTaskConfig = errorCode == "400000001" || errorMsg.contains("任务全局配置不存在")
        
        // 第一步：判断当前错误码是否需要自动加入黑名单
        val shouldAutoAdd = isUnsupportedRpc || isInvalidArgument || isTemplateNotExist || isPromoProdError || isNoTaskConfig || when (errorCode) {
            "CAMP_TRIGGER_ERROR",
            "104",
            "OP_REPEAT_CHECK",
            "PROMISE_HAS_PROCESSING_TEMPLATE" -> true
            "TASK_ID_INVALID" -> true
            else -> false
        }

        // 第二步：如果确定需要自动加入黑名单
        if (shouldAutoAdd) {
            // 调用添加方法，将任务ID and 标题组合后加入黑名单（支持模糊匹配）
            addToBlacklist(taskId, taskTitle)
            // 第三步：根据错误码及详情生成用户友好的错误说明
            val reason = when {
                isUnsupportedRpc -> "不支持rpc调用"
                isInvalidArgument -> "不是有效入参"
                isTemplateNotExist -> "生活记录模板不存在"
                isPromoProdError -> "参数错误(promoprod)"
                isNoTaskConfig -> "任务全局配置不存在"
                errorCode == "CAMP_TRIGGER_ERROR" -> "海豚活动触发错误"
                errorCode == "OP_REPEAT_CHECK" -> "操作太频繁"
                errorCode == "104" || errorCode == "PROMISE_HAS_PROCESSING_TEMPLATE" -> "存在进行中的生活记录"
                errorCode == "TASK_ID_INVALID" -> "海豚任务ID非法"
                else -> "未知错误"
            }

            // 第四步：生成日志信息并记录
            // 优先显示完整信息（ID-标题），如果标题为空则只显示ID
            val taskInfo = if (taskTitle.isNotBlank()) "$taskId - $taskTitle" else taskId
            Log.runtime(TAG, "任务[$taskInfo]因$reason 自动加入黑名单")
        }
    }
}