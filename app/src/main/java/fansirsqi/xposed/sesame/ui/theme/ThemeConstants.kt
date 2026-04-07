package fansirsqi.xposed.sesame.ui.theme

/**
 * 主题中心常量定义
 */
object ThemeConstants {

    // 支付宝内部存储路径
    const val INTERNAL_STORAGE_PATH = "/data/data/com.eg.android.AlipayGphone/files/skin_center_dir"

    // 外部存储路径（SD卡）
    // 使用通过 API 获取的路径，避免在 Xposed Hook 或者双开环境下路径错误
    val EXTERNAL_STORAGE_PATH: String
        get() = "${android.os.Environment.getExternalStorageDirectory().absolutePath}/Android/media/com.eg.android.AlipayGphone/000_HOHO_THEME_CENTER"

    // 主题文件夹路径
    const val THEMES_FOLDER = "themes"

    // 操作标记文件路径
    val EXPORT_PATH: String
        get() = "$EXTERNAL_STORAGE_PATH/export"
    val DELETE_PATH: String
        get() = "$EXTERNAL_STORAGE_PATH/delete"
    val UPDATE_PATH: String
        get() = "$EXTERNAL_STORAGE_PATH/update"

    // 选中的主题文件
    const val SELECTED_THEME_FILE = "selected_theme"

    // SharedPreferences 名称
    const val PREFS_NAME = "theme_prefs"
    const val KEY_FIRST_RUN = "theme_first_run"

    // 导出目录
    const val EXPORTED_THEMES_FOLDER = "exported_themes"
}

/**
 * 主题操作类型
 */
enum class ThemeOperation(val displayName: String, val filePath: String) {
    EXPORT("导出主题", ThemeConstants.EXPORT_PATH),
    DELETE("删除主题缓存", ThemeConstants.DELETE_PATH),
    UPDATE("更新主题缓存", ThemeConstants.UPDATE_PATH)
}
