package fansirsqi.xposed.sesame.ui.extra.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import fansirsqi.xposed.sesame.ui.logviewer.LogViewerComposeActivity
import fansirsqi.xposed.sesame.ui.extra.Callbacks
import fansirsqi.xposed.sesame.ui.extra.RequestItem
import fansirsqi.xposed.sesame.ui.extra.viewmodel.RpcDebugViewModel
import fansirsqi.xposed.sesame.ui.extra.ui.RpcDebugScreenBinder
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.ToastUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.extra.RequestStorage

/**
 * 全量 Compose 化的 Rpc 调试页面（模拟请求）。
 */
class RpcDebugActivity : AppCompatActivity() {
    private val requests = mutableListOf<RequestItem>()
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var vm: RpcDebugViewModel

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fansirsqi.xposed.sesame.data.ViewAppInfo.init(applicationContext)

        vm = ViewModelProvider(this).get(RpcDebugViewModel::class.java)

        // 初始化数据（从存储加载）
        requests.addAll(RequestStorage.loadRequests(this))

        // 使用 ComposeView 作为根视图
        val composeView = ComposeView(this)
        setContentView(composeView)

        // 绑定全屏 Compose 界面
        RpcDebugScreenBinder.bindFullScreen(composeView, requests, object : Callbacks {
            override fun onSend(id: Int) {
                // id == -1 表示从输入框发送
                val item = if (id == -1) RequestItem("即时请求", vm.method.value, vm.data.value)
                else findById(id)
                if (item != null) sendRequest(item.method, item.data)
            }
            override fun onEdit(id: Int) { /* 使用 Compose 弹窗编辑 */ }
            override fun onDelete(id: Int) {
                vm.delete(id)
            }
            override fun onDuplicate(id: Int) { vm.duplicate(id) }
            override fun onToggle(id: Int) { viewLog(id) }
        })

        // 注册广播接收器，将结果写入 ViewModel
        registerRpcReceiver()
    }

    private fun findById(id: Int): RequestItem? {
        return vm.items.value.firstOrNull { it.id == id }
    }

    private fun registerRpcReceiver() {
        val intentFilter = IntentFilter("com.eg.android.AlipayGphone.sesame.rpcresponse")
        // 注册广播接收器（赋值给成员变量，确保可以在 onDestroy 中注销）
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                Log.runtime("receive broadcast:$action intent:$intent")
                when (action) {
                    "com.eg.android.AlipayGphone.sesame.rpcresponse" -> {
                        val result = intent.getStringExtra("result")
                        vm.updateResult(result ?: "收到广播但无数据")
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
    }

    private fun sendRequest(method: String, data: String) {
        try {
            val trimmed = data.trim()
            if (trimmed.isNotEmpty()) {
                val repaired = repairJson(trimmed)
                val isValid = try {
                    if (repaired.startsWith("[")) {
                        org.json.JSONArray(repaired)
                        true
                    } else if (repaired.startsWith("{")) {
                        org.json.JSONObject(repaired)
                        true
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
                if (!isValid) {
                    ToastUtil.makeText(this, "数据非合法 JSON 格式 (必须以 [ 或 { 开头且语法正确)！", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest")
            intent.putExtra("method", method)
            intent.putExtra("data", data)
            intent.putExtra("type", "Rpc")
            sendBroadcast(intent)
            ToastUtil.makeText(this, "发送--请求发送成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            vm.updateResult("发送请求错误")
            Log.other("发送请求错误:" + e)
        }
    }

    private fun repairJson(s: String): String {
        try {
            val pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"(\\{.*?\\})\"")
            val matcher = pattern.matcher(s)
            val sb = java.lang.StringBuffer()
            while (matcher.find()) {
                val key = matcher.group(1)
                val inner = matcher.group(2)
                val innerUnescaped = inner.replace("\\\"", "\"")
                val innerEscaped = innerUnescaped.replace("\"", "\\\"")
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("\"$key\":\"$innerEscaped\""))
            }
            matcher.appendTail(sb)
            return sb.toString()
        } catch (e: Exception) {
            return s
        }
    }

    private fun viewLog(id: Int = 1) {
        val intent = Intent(this, LogViewerComposeActivity::class.java)
        intent.putExtra("canClear", true)

        val uri = when (id) {
            1 -> ("file://" + Files.getCaptureLogFile().absolutePath).toUri() // 抓包日志
            2 -> ("file://" + Files.getDebugLogFile().absolutePath).toUri()   // Debug 日志
            else -> ("file://" + Files.getCaptureLogFile().absolutePath).toUri()
        }

        intent.data = uri
        intent.putExtra("showTest", false)
        startActivity(intent)
    }

    override fun onPause() {
        super.onPause()
        // 保存所有请求，包括固定请求的修改
        RequestStorage.saveRequests(this, vm.items.value)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::broadcastReceiver.isInitialized) {
            try { unregisterReceiver(broadcastReceiver) } catch (ignored: IllegalArgumentException) { }
        }
    }
}
