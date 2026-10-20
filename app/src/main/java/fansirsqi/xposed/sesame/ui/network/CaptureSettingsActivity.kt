package fansirsqi.xposed.sesame.ui.network

import android.os.Bundle
import androidx.activity.compose.setContent
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme

class CaptureSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SesameTheme {
                CaptureSettingsScreen(onBack = { finish() })
            }
        }
    }
}
