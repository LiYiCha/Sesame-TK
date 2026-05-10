package fansirsqi.xposed.sesame.ui.network

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme

class CaptureListActivity : BaseActivity() {

    private val viewModel: CaptureListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDate = intent.getStringExtra("date")
        viewModel.loadData(initialDate)

        setContent {
            SesameTheme {
                CaptureListScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onRecordClick = { record ->
                        val intent = android.content.Intent(this, CaptureDetailActivity::class.java).apply {
                            putExtra("recordId", record.id)
                            putExtra("recordDate", viewModel.viewingDate.value)
                        }
                        startActivity(intent)
                    },
                    onNewRequest = {
                        val intent = android.content.Intent(this, CaptureDetailActivity::class.java).apply {
                            putExtra("recordId", "")
                            putExtra("recordDate", "")
                            putExtra("newRequest", true)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
