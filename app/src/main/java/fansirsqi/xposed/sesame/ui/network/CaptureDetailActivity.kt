package fansirsqi.xposed.sesame.ui.network

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme

class CaptureDetailActivity : BaseActivity() {

    private val viewModel: CaptureDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isNewRequest = intent.getBooleanExtra("newRequest", false)

        if (isNewRequest) {
            viewModel.loadRecord("", "")
        } else {
            val recordId = intent.getStringExtra("recordId") ?: return finish()
            val recordDate = intent.getStringExtra("recordDate") ?: return finish()
            viewModel.loadRecord(recordId, recordDate)
        }

        setContent {
            SesameTheme {
                CaptureDetailScreen(
                    detailViewModel = viewModel,
                    onBack = { finish() },
                    isNewRequest = isNewRequest
                )
            }
        }
    }
}
