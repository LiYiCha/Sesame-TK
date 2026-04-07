package fansirsqi.xposed.sesame.ui.skin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 皮肤设置 ViewModel
 *
 * 负责管理皮肤设置页面的状态和业务逻辑
 * 使用 StateFlow 提供响应式的状态更新
 */
class SkinViewModel(context: Context) : ViewModel() {

    private val repository = SkinRepository(context)

    // 皮肤状态的 StateFlow
    private val _state = MutableStateFlow(SkinState())
    val state: StateFlow<SkinState> = _state.asStateFlow()

    init {
        // 初始化状态
        loadInitialState()
    }

    /**
     * 加载初始状态
     *
     * 从 Repository 读取当前的会员等级、操作状态等
     */
    private fun loadInitialState() {
        viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    selectedGrade = repository.getCurrentMemberGrade(),
                    operationStates = repository.getAllOperationStates(),
                    isResourceInstalled = repository.isResourceInstalled(),
                    isFirstRun = repository.isFirstRun()
                )
            }
            // 加载可用皮肤列表
            loadAvailableSkins()
        }
    }

    /**
     * 标记已经不是首次运行
     */
    fun markNotFirstRun() {
        repository.markNotFirstRun()
        _state.update { it.copy(isFirstRun = false) }
    }

    /**
     * 更新会员等级
     *
     * @param grade 新的会员等级
     */
    fun updateMemberGrade(grade: MemberGrade) {
        viewModelScope.launch {
            try {
                repository.updateMemberGrade(grade)
                _state.update { it.copy(selectedGrade = grade) }
            } catch (e: Exception) {
                // 错误处理
                e.printStackTrace()
            }
        }
    }

    /**
     * 切换操作状态
     *
     * @param operation 操作类型
     */
    fun toggleOperation(operation: SkinOperation) {
        viewModelScope.launch {
            try {
                val newState = repository.toggleOperation(operation)
                _state.update { currentState ->
                    val newOperationStates = currentState.operationStates.toMutableMap()
                    newOperationStates[operation] = newState
                    currentState.copy(operationStates = newOperationStates)
                }
            } catch (e: Exception) {
                // 错误处理
                e.printStackTrace()
            }
        }
    }

    /**
     * 执行皮肤操作
     *
     * 立即创建操作请求，并返回操作结果
     *
     * @param operation 操作类型
     * @param callback 操作完成回调 (成功标志, 提示消息)
     */
    fun executeOperation(operation: SkinOperation, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val (success, message) = repository.executeOperation(operation)

                // 更新操作状态
                _state.update { currentState ->
                    val newOperationStates = currentState.operationStates.toMutableMap()
                    newOperationStates[operation] = true
                    currentState.copy(operationStates = newOperationStates)
                }

                callback(success, message)
            } catch (e: Exception) {
                callback(false, "操作异常: ${e.message}")
            }
        }
    }

    /**
     * 下载资源包
     *
     * 启动下载流程，并更新下载状态
     */
    fun downloadResource() {
        viewModelScope.launch {
            repository.downloadAndExtractResource().collect { downloadState ->
                _state.update { it.copy(downloadState = downloadState) }

                // 下载成功后更新资源安装状态
                if (downloadState is DownloadState.Success) {
                    _state.update { it.copy(isResourceInstalled = repository.isResourceInstalled()) }
                }
            }
        }
    }

    /**
     * 重置下载状态
     *
     * 将下载状态重置为空闲
     */
    fun resetDownloadState() {
        _state.update { it.copy(downloadState = DownloadState.Idle) }
    }

    /**
     * 刷新状态
     *
     * 重新从 Repository 读取所有状态
     */
    fun refreshState() {
        loadInitialState()
    }

    /**
     * 获取资源文件夹路径
     */
    fun getResourceFolderPath(): String {
        return repository.getResourceFolderPath()
    }

    /**
     * 加载可用皮肤列表
     *
     * 扫描皮肤目录并更新状态
     */
    fun loadAvailableSkins() {
        viewModelScope.launch {
            try {
                val skins = repository.scanAvailableSkins()
                val selectedSkin = repository.getSelectedSkinName()
                _state.update { 
                    it.copy(
                        availableSkins = skins,
                        selectedSkinName = selectedSkin
                    ) 
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 选择皮肤
     *
     * 保存用户选择的皮肤
     *
     * @param skinName 皮肤名称
     */
    fun selectSkin(skinName: String) {
        viewModelScope.launch {
            try {
                repository.selectSkin(skinName)
                _state.update {
                    it.copy(selectedSkinName = skinName)
                }
                // 重新加载皮肤列表以更新选中状态
                loadAvailableSkins()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 从ZIP文件导入皮肤
     *
     * @param uri ZIP文件的URI
     * @param callback 导入完成回调 (成功标志, 消息)
     */
    fun importSkinFromZip(uri: android.net.Uri, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val (success, message) = repository.importSkinFromZip(uri)
                callback(success, message)
                if (success) {
                    loadAvailableSkins()
                }
            } catch (e: Exception) {
                callback(false, "导入异常: ${e.message}")
            }
        }
    }

    /**
     * 从目录导入皮肤
     *
     * @param uri 目录的URI
     * @param callback 导入完成回调 (成功标志, 消息)
     */
    fun importSkinFromDirectory(uri: android.net.Uri, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val (success, message) = repository.importSkinFromDirectory(uri)
                callback(success, message)
                if (success) {
                    loadAvailableSkins()
                }
            } catch (e: Exception) {
                callback(false, "导入异常: ${e.message}")
            }
        }
    }

}
