package fansirsqi.xposed.sesame.ui.network

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.ui.BaseActivity

/**
 * 抓包详情页：展示请求概览、请求体、响应体 (已迁移到 Compose)
 */
class NetworkDetailActivity : BaseActivity() {

    private val viewModel: NetworkDetailViewModel by viewModels()
    private lateinit var packet: CapturePacket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 获取传递的数据包
        packet = intent.getSerializableExtra("packet") as? CapturePacket ?: return finish()

        // 2. 渲染 UI
        setContent {
            MaterialTheme {
                NetworkDetailScreen(
                    viewModel = viewModel,
                    packet = packet,
                    onBack = { finish() }
                )
            }
        }
    }
}
