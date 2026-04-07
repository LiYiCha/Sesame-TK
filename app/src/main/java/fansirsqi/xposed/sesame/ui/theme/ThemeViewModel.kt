package fansirsqi.xposed.sesame.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 主题管理 ViewModel
 *
 * 管理主题相关的状态和业务逻辑
 */
class ThemeViewModel(private val repository: ThemeRepository) : ViewModel() {

    private val _state = MutableStateFlow(ThemeState())
    val state: StateFlow<ThemeState> = _state.asStateFlow()

    private val _operationStates = MutableStateFlow<Map<ThemeOperation, Boolean>>(emptyMap())
    val operationStates: StateFlow<Map<ThemeOperation, Boolean>> = _operationStates.asStateFlow()

    init {
        loadAvailableThemes()
        loadOperationStates()
    }

    /**
     * 加载可用主题
     */
    fun loadAvailableThemes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val themes = repository.scanAvailableThemes()
                val selectedThemeId = repository.getSelectedThemeId()
                _state.update {
                    it.copy(
                        availableThemes = themes,
                        selectedThemeId = selectedThemeId,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "加载主题失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 加载操作状态
     */
    private fun loadOperationStates() {
        viewModelScope.launch {
            val states = repository.getAllOperationStates()
            _operationStates.update { states }
        }
    }

    /**
     * 选择主题
     *
     * 优化：只更新选中状态，不重新扫描所有主题
     */
    fun selectTheme(themeId: String) {
        viewModelScope.launch {
            try {
                repository.selectTheme(themeId)

                // 只更新选中状态，避免重新扫描文件系统
                _state.update { currentState ->
                    currentState.copy(
                        availableThemes = currentState.availableThemes.map { theme ->
                            theme.copy(isSelected = theme.themeId == themeId)
                        },
                        selectedThemeId = themeId
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(errorMessage = "选择主题失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 执行主题操作
     */
    fun executeOperation(operation: ThemeOperation, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (success, message) = repository.executeOperation(operation)
            callback(success, message)
            loadOperationStates()
        }
    }

    /**
     * 导入主题（ZIP文件）
     */
    fun importTheme(uri: android.net.Uri, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val (success, message) = repository.importThemeFromZip(uri)
            _state.update { it.copy(isLoading = false) }
            callback(success, message)
            if (success) {
                loadAvailableThemes()
            }
        }
    }

    /**
     * 导入主题（目录）
     */
    fun importThemeFromDirectory(uri: android.net.Uri, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val (success, message) = repository.importThemeFromDirectory(uri)
            _state.update { it.copy(isLoading = false) }
            callback(success, message)
            if (success) {
                loadAvailableThemes()
            }
        }
    }

    /**
     * 删除主题
     */
    fun deleteTheme(themeId: String, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (success, message) = repository.deleteTheme(themeId)
            callback(success, message)
            if (success) {
                loadAvailableThemes()
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
