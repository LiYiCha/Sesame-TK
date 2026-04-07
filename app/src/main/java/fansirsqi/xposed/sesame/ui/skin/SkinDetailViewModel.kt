package fansirsqi.xposed.sesame.ui.skin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 皮肤详情 ViewModel
 *
 * 管理单个皮肤的详细信息和图片替换操作
 */
class SkinDetailViewModel(
    private val context: Context,
    private val skinName: String
) : ViewModel() {

    private val repository = SkinRepository(context)

    // 皮肤详情状态
    private val _state = MutableStateFlow(SkinDetailState())
    val state: StateFlow<SkinDetailState> = _state.asStateFlow()

    init {
        loadSkinDetail()
    }

    /**
     * 加载皮肤详情
     */
    fun loadSkinDetail() {
        viewModelScope.launch {
            try {
                val skinDir = File(SkinConstants.EXTERNAL_STORAGE_PATH, skinName)
                if (!skinDir.exists()) {
                    _state.update { it.copy(error = "皮肤文件夹不存在") }
                    return@launch
                }

                // 读取元数据
                val metadata = repository.readSkinMetadata(skinDir)

                // 查找所有图片
                val images = findAllImages(skinDir)

                _state.update {
                    it.copy(
                        skinName = skinName,
                        description = metadata.description,
                        themeColor = metadata.themeColor,
                        images = images,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "加载失败: ${e.message}") }
            }
        }
    }

    /**
     * 查找皮肤中的所有图片
     */
    private fun findAllImages(skinDir: File): List<SkinImageInfo> {
        val images = mutableListOf<SkinImageInfo>()

        // 定义要查找的图片及其可能的文件名变体（包括有后缀和无后缀的版本）
        val imageDefinitions = listOf(
            ImageDefinition("background_2x1.png", "付款码背景 (2:1)", ImageType.BACKGROUND,
                listOf("background_2x1.png", "background_2x1", "background_2x1_1.png", "background_2x1_2.png")),
            ImageDefinition("background_16x9.png", "付款码背景 (16:9)", ImageType.BACKGROUND,
                listOf("background_16x9.png", "background_16x9", "background_16x9_1.png", "background_16x9_2.png")),
            ImageDefinition("background_4x3.png", "付款码背景 (4:3)", ImageType.BACKGROUND,
                listOf("background_4x3.png", "background_4x3", "background_4x3_1.png", "background_4x3_2.png")),
            ImageDefinition("logo_nov30th.png", "中间Logo", ImageType.LOGO,
                listOf("logo_nov30th.png", "logo_nov30th", "logo_hoho.png", "logo_hoho", "logo_2.png", "logo.png", "logo")),
            ImageDefinition("mask_hoho_color.png", "右侧水印", ImageType.MASK,
                listOf("mask_hoho_color.png", "mask_hoho_color", "mask_hoho.png", "mask_hoho", "mask_2.png", "mask.png", "mask"))
        )

        imageDefinitions.forEach { def ->
            // 尝试查找文件名变体
            var foundFile: File? = null
            var actualFileName = def.fileName

            for (variant in def.variants) {
                val file = File(skinDir, variant)
                if (file.exists()) {
                    foundFile = file
                    actualFileName = variant
                    break
                }
            }

            if (foundFile != null) {
                images.add(
                    SkinImageInfo(
                        fileName = actualFileName,
                        displayName = def.displayName,
                        type = def.type,
                        path = foundFile.absolutePath,
                        exists = true
                    )
                )
            } else {
                images.add(
                    SkinImageInfo(
                        fileName = def.fileName,
                        displayName = def.displayName,
                        type = def.type,
                        path = "",
                        exists = false
                    )
                )
            }
        }

        return images
    }

    /**
     * 替换图片
     *
     * @param imageName 图片文件名
     * @param uri 新图片的URI
     * @param callback 完成回调
     */
    fun replaceImage(imageName: String, uri: Uri, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val skinDir = File(SkinConstants.EXTERNAL_STORAGE_PATH, skinName)
                val targetFile = File(skinDir, imageName)

                // 从URI复制图片
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                callback(true, "")
            } catch (e: Exception) {
                callback(false, e.message ?: "未知错误")
            }
        }
    }
}

/**
 * 皮肤详情状态
 */
data class SkinDetailState(
    val skinName: String = "",
    val description: String = "",
    val themeColor: String = "#000000",
    val images: List<SkinImageInfo> = emptyList(),
    val error: String? = null
)

/**
 * 皮肤图片信息
 */
data class SkinImageInfo(
    val fileName: String,
    val displayName: String,
    val type: ImageType,
    val path: String,
    val exists: Boolean
)

/**
 * 图片类型
 */
enum class ImageType {
    BACKGROUND,
    LOGO,
    MASK
}

/**
 * 图片定义
 */
private data class ImageDefinition(
    val fileName: String,
    val displayName: String,
    val type: ImageType,
    val variants: List<String> = listOf(fileName)
)
