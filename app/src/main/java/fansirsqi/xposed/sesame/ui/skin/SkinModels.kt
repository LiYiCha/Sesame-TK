package fansirsqi.xposed.sesame.ui.skin

import android.os.Environment

/**
 * 皮肤模块数据模型
 *
 * 定义皮肤模块相关的数据结构和常量
 */
object SkinConstants {
    // 存储路径
    val EXTERNAL_STORAGE_PATH = "${Environment.getExternalStorageDirectory()}/Android/media/com.eg.android.AlipayGphone/000_HOHO_ALIPAY_SKIN"
    val EXTRACT_PATH = "${Environment.getExternalStorageDirectory()}/Android/media/com.eg.android.AlipayGphone/"

    // 控制文件路径
    val EXPORT_FILE = "$EXTERNAL_STORAGE_PATH/export"
    val DELETE_FILE = "$EXTERNAL_STORAGE_PATH/delete"
    val UPDATE_FILE = "$EXTERNAL_STORAGE_PATH/update"
    val ACTIVATE_FILE = "$EXTERNAL_STORAGE_PATH/actived"

    // 下载 URL
    const val DOWNLOAD_URL = "https://github.com/LiYiCha/AlipayHighHeadsomeRichAndroid/raw/master/SD%E5%8D%A1%E8%B5%84%E6%BA%90%E6%96%87%E4%BB%B6%E5%8C%85/SD%E8%B5%84%E6%BA%90%E6%96%87%E4%BB%B6.zip"

    // GitHub 仓库 URL
    const val GITHUB_REPO_URL = "https://github.com/LiYiCha/AlipayHighHeadsomeRichAndroid"

    // SharedPreferences 键
    const val PREFS_NAME = "SkinPreferences"
    const val KEY_FIRST_RUN = "isFirstRun"

    // 权限请求码
    const val PERMISSION_REQUEST_CODE = 1001
}

/**
 * 会员等级枚举
 *
 * 定义支付宝会员等级类型
 */
enum class MemberGrade(val displayName: String, val folderName: String?) {
    ORIGINAL("原有", null),
    PRIMARY("普通 (primary)", "level_primary"),
    GOLDEN("黄金 (golden)", "level_golden"),
    PLATINUM("铂金 (platinum)", "level_platinum"),
    DIAMOND("钻石 (diamond)", "level_diamond");

    companion object {
        /**
         * 从显示名称获取会员等级
         */
        fun fromDisplayName(name: String): MemberGrade {
            return values().find { it.displayName == name } ?: ORIGINAL
        }

        /**
         * 获取所有显示名称
         */
        fun getAllDisplayNames(): List<String> {
            return values().map { it.displayName }
        }
    }
}

/**
 * 皮肤操作类型
 *
 * 定义皮肤模块支持的操作类型
 */
enum class SkinOperation(val displayName: String, val filePath: String) {
    EXPORT("导出现有皮肤", SkinConstants.EXPORT_FILE),
    DELETE("删除皮肤缓存", SkinConstants.DELETE_FILE),
    UPDATE("更新皮肤缓存", SkinConstants.UPDATE_FILE),
    ACTIVATE("启用/禁用皮肤", SkinConstants.ACTIVATE_FILE)
}

/**
 * 下载状态
 *
 * 表示资源包下载的状态
 */
sealed class DownloadState {
    /** 空闲状态 */
    object Idle : DownloadState()

    /** 下载中 */
    data class Downloading(val progress: Int) : DownloadState()

    /** 下载成功 */
    object Success : DownloadState()

    /** 下载失败 */
    data class Error(val message: String) : DownloadState()
}

/**
 * 皮肤信息
 *
 * 表示单个皮肤的详细信息
 */
data class SkinInfo(
    /** 皮肤名称（文件夹名） */
    val name: String,

    /** 皮肤描述（从 meta.json 读取） */
    val description: String = "",

    /** 主题色（从 meta.json 读取） */
    val themeColor: String = "#000000",

    /** 预览图路径 */
    val previewImagePath: String? = null,

    /** 是否为当前选中的皮肤 */
    val isSelected: Boolean = false
)

/**
 * 皮肤元数据（meta.json 结构）
 */
data class SkinMetadata(
    val description: String = "",
    val themeColor: String = "#000000"
)

/**
 * 皮肤状态
 *
 * 表示皮肤模块的当前状态
 */
data class SkinState(
    /** 当前选中的会员等级 */
    val selectedGrade: MemberGrade = MemberGrade.ORIGINAL,

    /** 各操作的启用状态 */
    val operationStates: Map<SkinOperation, Boolean> = emptyMap(),

    /** 下载状态 */
    val downloadState: DownloadState = DownloadState.Idle,

    /** 资源包是否已安装 */
    val isResourceInstalled: Boolean = false,

    /** 是否首次运行 */
    val isFirstRun: Boolean = true,

    /** 可用皮肤列表 */
    val availableSkins: List<SkinInfo> = emptyList(),

    /** 当前选中的皮肤名称 */
    val selectedSkinName: String? = null
)
