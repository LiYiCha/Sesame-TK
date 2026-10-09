package fansirsqi.xposed.sesame.ui.theme.alipay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme

/**
 * 主题中心 Activity
 */
class AlipayThemeActivity : ComponentActivity() {

    private val factory = viewModelFactory {
        initializer {
            // 仓库持应用级 Context，避免 ViewModel 越过 Activity 生命周期引用它
            val context = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] ?: application
            AlipayThemeViewModel(AlipayThemeRepository(context))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SesameTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val viewModel: AlipayThemeViewModel = viewModel(factory = factory)
                    AlipayThemeScreen(
                        viewModel = viewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
