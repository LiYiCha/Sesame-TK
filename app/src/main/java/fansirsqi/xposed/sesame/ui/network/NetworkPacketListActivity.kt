package fansirsqi.xposed.sesame.ui.network

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import fansirsqi.xposed.sesame.ui.BaseActivity

import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme

/**
 * 抓包流水列表页：直接入口
 */
class NetworkPacketListActivity : BaseActivity() {

    private val viewModel: NetworkPacketViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 获取选传参数（默认 null 触发自动寻址）
        val initialDate = intent.getStringExtra("date")

        // 2. 触发加载逻辑（ViewModel 会自动定位到今日或最新历史）
        viewModel.loadData(initialDate)

        // 3. 渲染 Compose UI
        setContent {
            SesameTheme {
                NetworkPacketListScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onPacketClick = { packet ->
                        val intent = Intent(this, NetworkDetailActivity::class.java)
                        intent.putExtra("packet", packet)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
