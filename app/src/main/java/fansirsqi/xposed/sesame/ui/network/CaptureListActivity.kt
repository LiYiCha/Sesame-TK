package fansirsqi.xposed.sesame.ui.network

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import fansirsqi.xposed.sesame.ui.BaseActivity
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme

class CaptureListActivity : BaseActivity() {

    private val viewModel: CaptureListViewModel by viewModels()
    private var captureReceiver: android.content.BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDate = intent.getStringExtra("date")
        viewModel.loadData(initialDate)

        // 注册实时抓包广播
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                if (intent.action == "fansirsqi.xposed.sesame.NEW_CAPTURE") {
                    intent.getStringExtra("record_json")?.let { json ->
                        viewModel.addRecordFromJson(json)
                    }
                }
            }
        }
        captureReceiver = receiver
        val filter = android.content.IntentFilter("fansirsqi.xposed.sesame.NEW_CAPTURE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

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
    override fun onDestroy() {
        super.onDestroy()
        captureReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        captureReceiver = null
    }
}
