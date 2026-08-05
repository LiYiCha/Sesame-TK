package fansirsqi.xposed.sesame.ui.theme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme
import fansirsqi.xposed.sesame.ui.theme.ui.ThemeScreen

/**
 * 主题中心 Activity
 */
class ThemeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = ThemeRepository(this)
        val viewModel = ThemeViewModel(repository)

        setContent {
            SesameTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    ThemeScreen(
                        viewModel = viewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
