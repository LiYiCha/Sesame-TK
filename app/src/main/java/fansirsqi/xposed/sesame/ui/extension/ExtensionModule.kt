package fansirsqi.xposed.sesame.ui.extension

/**
 * 扩展模块数据类
 *
 * 表示一个可扩展的功能模块，包含模块的基本信息和配置
 *
 * @property id 模块唯一标识符
 * @property name 模块名称
 * @property description 模块描述
 * @property prefKey SharedPreferences 中的键名，用于保存启用状态（如果为 null，则模块没有开关）
 * @property activityClass 模块设置页面的 Activity 类
 * @property icon 模块图标资源 ID（可选）
 */
data class ExtensionModule(
    val id: String,
    val name: String,
    val description: String,
    val prefKey: String? = null,
    val activityClass: Class<*>,
    val icon: Int? = null
)

/**
 * 扩展模块状态
 *
 * 表示模块的当前状态，包括是否启用
 *
 * @property module 模块信息
 * @property isEnabled 是否启用
 */
data class ExtensionModuleState(
    val module: ExtensionModule,
    val isEnabled: Boolean
)
