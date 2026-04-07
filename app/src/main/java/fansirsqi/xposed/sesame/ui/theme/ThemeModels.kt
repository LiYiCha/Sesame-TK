package fansirsqi.xposed.sesame.ui.theme

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 主题信息
 */
data class ThemeInfo(
    val themeId: String,
    val name: String,
    val description: String,
    val previewImagePath: String? = null,
    val isSelected: Boolean = false
)

/**
 * 主题资源项
 */
data class ThemeResource(
    val position: String,
    val type: String,  // color, image, lottieVideo
    val color: String? = null,
    val image: String? = null,
    val lottie: String? = null,
    val lottieVideo: String? = null,
    val description: String? = null,
    val metaList: List<ThemeResourceMeta>? = null,
    // 暗色模式资源（使用 @JsonProperty 映射 dark#xxx 字段）
    @JsonProperty("dark#color")
    val darkColor: String? = null,
    @JsonProperty("dark#image")
    val darkImage: String? = null,
    @JsonProperty("dark#lottieVideo")
    val darkLottieVideo: String? = null
)

/**
 * 主题资源元数据
 */
data class ThemeResourceMeta(
    val image: String? = null,
    val aspectRatio: Int? = null,
    @JsonProperty("dark#image")
    val darkImage: String? = null
)

/**
 * 主题元数据
 */
data class ThemeMetadata(
    val skinId: String = "",
    val description: String = "",
    val resource: List<ThemeResource> = emptyList()
)

/**
 * 主题状态
 */
data class ThemeState(
    val availableThemes: List<ThemeInfo> = emptyList(),
    val selectedThemeId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 下载状态
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}
