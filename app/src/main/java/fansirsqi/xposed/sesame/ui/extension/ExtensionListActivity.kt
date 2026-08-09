package fansirsqi.xposed.sesame.ui.extension

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 扩展功能列表 Activity
 *
 * 使用 Jetpack Compose 构建的扩展功能管理页面
 * 显示所有可用的扩展模块，用户可以启用/禁用模块并进入详细设置
 *
 * 架构：
 * - Activity: 负责生命周期管理和 Compose 集成
 * - ViewModel: 负责业务逻辑和状态管理
 * - Screen: 负责 UI 渲染
 *
 * 注意：此 Activity 直接继承 ComponentActivity 而不是 BaseActivity
 * 因为 Compose 不需要 BaseActivity 提供的 toolbar 功能
 */
class ExtensionListActivity : ComponentActivity() {

    // ViewModel 实例
    private lateinit var viewModel: ExtensionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 ViewModel
        viewModel = ExtensionViewModel()

        // 设置 Compose UI
        setContent {
            // 使用全局统一的 SesameTheme
            fansirsqi.xposed.sesame.ui.theme.app.SesameTheme {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 渲染主屏幕
                    ExtensionListScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 刷新模块状态
        viewModel.refreshModules()
    }
}
