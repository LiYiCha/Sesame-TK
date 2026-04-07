package fansirsqi.xposed.sesame.ui.extension

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.data.Config
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.ui.skin.SkinActivity
import fansirsqi.xposed.sesame.ui.theme.ThemeActivity
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.UserMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 扩展功能列表 ViewModel
 *
 * 负责管理扩展模块的状态和业务逻辑
 * 使用 StateFlow 提供响应式的状态更新
 */
class ExtensionViewModel : ViewModel() {

    // 模块状态列表的 StateFlow
    private val _moduleStates = MutableStateFlow<List<ExtensionModuleState>>(emptyList())
    val moduleStates: StateFlow<List<ExtensionModuleState>> = _moduleStates.asStateFlow()

    init {
        // 确保 Config 已加载
        // 注意：在扩展功能页面，可能还没有登录支付宝，所以 currentUid 可能为 null
        // 这种情况下使用 null 加载默认配置是正常的
        if (!Config.isLoaded()) {
            val userId = UserMap.currentUid
            Config.load(userId)
            Log.runtime("ExtensionViewModel", "配置已加载，userId: ${userId ?: "null(使用默认配置)"}")
        }
        // 初始化模块列表
        loadModules()
    }

    /**
     * 加载所有扩展模块
     *
     * 从配置中读取模块信息和启用状态
     */
    private fun loadModules() {
        val modules = listOf(
            // 皮肤模块
            ExtensionModule(
                id = "skin",
                name = "皮肤模块",
                description = "自定义支付宝付款码皮肤和会员等级显示",
                prefKey = "enableSkinModule",
                activityClass = SkinActivity::class.java
            ),
            // 主题中心模块
            ExtensionModule(
                id = "theme",
                name = "主题中心",
                description = "自定义支付宝整体外观主题",
                prefKey = null,  // 无开关，仅提供入口
                activityClass = ThemeActivity::class.java
            )
            // 未来可以在这里添加更多扩展模块
        )

        // 读取每个模块的启用状态
        val states = modules.map { module ->
            // 从 BaseModel 读取配置
            val isEnabled = when (module.id) {
                "skin" -> BaseModel.enableSkinModule.value
                else -> false
            }

            ExtensionModuleState(
                module = module,
                isEnabled = isEnabled
            )
        }

        _moduleStates.value = states
    }

    /**
     * 切换模块的启用状态
     *
     * @param moduleId 模块 ID
     * @param enabled 新的启用状态
     */
    fun toggleModule(moduleId: String, enabled: Boolean) {
        viewModelScope.launch {
            // 查找对应的模块，如果不存在则返回
            val currentStates = _moduleStates.value
            if (currentStates.none { it.module.id == moduleId }) return@launch

            // 获取用户ID（可能为 null）
            val userId = UserMap.currentUid

            // 确保 Config 已加载
            if (!Config.isLoaded()) {
                Config.load(userId)
            }

            // 更新 BaseModel 配置
            when (moduleId) {
                "skin" -> {
                    BaseModel.enableSkinModule.value = enabled
                    Log.runtime("ExtensionViewModel", "皮肤模块状态已更新: $enabled")
                }
            }

            // 保存配置到文件
            try {
                val saveSuccess = Config.save(userId, true)

                if (!saveSuccess) {
                    Log.error("ExtensionViewModel", "保存配置失败，userId: ${userId ?: "null"}")
                    return@launch
                }

                Log.runtime("ExtensionViewModel", "配置保存成功，userId: ${userId ?: "null(默认配置)"}")
            } catch (e: Exception) {
                Log.error("ExtensionViewModel", "保存配置异常: ${e.message}")
                Log.printStackTrace("ExtensionViewModel", e)
                return@launch
            }

            // 更新状态
            val newStates = currentStates.map { state ->
                if (state.module.id == moduleId) {
                    state.copy(isEnabled = enabled)
                } else {
                    state
                }
            }
            _moduleStates.value = newStates
        }
    }

    /**
     * 刷新模块状态
     *
     * 从配置重新读取所有模块的状态
     */
    fun refreshModules() {
        loadModules()
    }
}
