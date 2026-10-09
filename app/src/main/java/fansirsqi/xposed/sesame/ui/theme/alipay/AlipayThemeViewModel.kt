package fansirsqi.xposed.sesame.ui.theme.alipay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 一次性提示，由界面单点消费后弹 Toast
 */
sealed interface AlipayThemeEvent {
    data class Notice(val text: String, val isError: Boolean = false) : AlipayThemeEvent
}

data class AlipayThemeDetailState(
    val slots: AlipayThemeSlots? = null,
    val isLoading: Boolean = false,
    val applying: Boolean = false
)

class AlipayThemeViewModel(private val repository: AlipayThemeRepository) : ViewModel() {

    private val _state = MutableStateFlow(AlipayThemeState())
    val state: StateFlow<AlipayThemeState> = _state.asStateFlow()

    private val _detail = MutableStateFlow(AlipayThemeDetailState())
    val detail: StateFlow<AlipayThemeDetailState> = _detail.asStateFlow()

    private val _events = Channel<AlipayThemeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh(initial = true)
    }

    /**
     * initial 决定首屏用整页骨架还是顶部进度条，避免任何刷新都挡屏
     */
    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = initial && it.availablePacks.isEmpty(),
                    isRefreshing = !initial || it.availablePacks.isNotEmpty()
                )
            }
            try {
                val packs = repository.scanAvailablePacks()
                val selected = repository.getSelectedPackId()
                _state.update {
                    it.copy(
                        availablePacks = packs,
                        selectedPackId = selected,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
                emit("加载主题失败: ${e.message}", isError = true)
            }
        }
    }

    fun loadDetail(packId: String) {
        _detail.value = AlipayThemeDetailState(isLoading = true)
        viewModelScope.launch {
            val slots = try {
                repository.loadSlots(packId)
            } catch (e: Exception) {
                emit("读取主题资源失败: ${e.message}", isError = true)
                null
            }
            _detail.value = AlipayThemeDetailState(slots = slots)
        }
    }

    /**
     * 选中 + 推送支付宝进程，两步必须一起完成，只写选择文件主题不会生效
     */
    fun applyPack(packId: String) {
        viewModelScope.launch {
            _detail.update { it.copy(applying = true) }
            val result = runCatching { repository.applyPack(packId) }
                .getOrElse { Pair(false, "应用失败: ${it.message}") }
            _detail.update { it.copy(applying = false) }

            if (result.first) {
                markSelected(packId)
            }
            emit(result.second, isError = !result.first)
        }
    }

    fun themesRoot(): String =
        AlipayThemeConstants.EXTERNAL_STORAGE_PATH + "/" + AlipayThemeConstants.THEMES_FOLDER

    fun peekSlots(packId: String): AlipayThemeSlots? = repository.peekSlots(packId)

    /**
     * 导出会往磁盘写新包，成功后重扫；其余操作只回报结果
     */
    fun execute(operation: AlipayThemeOperation) {
        viewModelScope.launch {
            val result = runCatching { repository.executeThemeAction(operation) }
                .getOrElse { Pair(false, "${operation.displayName}失败: ${it.message}") }
            emit(result.second, isError = !result.first)
            if (result.first && operation == AlipayThemeOperation.EXPORT) refresh()
        }
    }

    fun deletePack(packId: String) {
        viewModelScope.launch {
            val result = runCatching { repository.deletePack(packId) }
                .getOrElse { Pair(false, "删除失败: ${it.message}") }
            emit(result.second, isError = !result.first)
            if (result.first) refresh()
        }
    }

    fun importFromZip(uri: android.net.Uri) = import { repository.importFromZip(uri) }

    fun importFromDirectory(uri: android.net.Uri) = import { repository.importFromDirectory(uri) }

    private fun import(action: suspend () -> Pair<Boolean, String>) {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val result = runCatching { action() }.getOrElse { Pair(false, "导入失败: ${it.message}") }
            emit(result.second, isError = !result.first)
            if (result.first) refresh() else _state.update { it.copy(isRefreshing = false) }
        }
    }

    private fun markSelected(packId: String) {
        _state.update { current ->
            current.copy(
                selectedPackId = packId,
                availablePacks = current.availablePacks.map { it.copy(isSelected = it.packId == packId) }
            )
        }
    }

    private fun emit(text: String, isError: Boolean = false) {
        viewModelScope.launch { _events.send(AlipayThemeEvent.Notice(text, isError)) }
    }
}
