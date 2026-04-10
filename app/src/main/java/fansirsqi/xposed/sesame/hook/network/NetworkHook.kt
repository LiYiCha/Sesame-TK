package fansirsqi.xposed.sesame.hook.network

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.util.Log
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用内网络请求拦截模块
 * 负责拦截 HTTP/HTTPS (HttpURLConnection) 和 支付宝 RPC 请求
 */
object NetworkHook {
    private const val TAG = "NetworkHook"
    private var isHooked = false
    // 关键加固：防止重复 Hook 导致卡顿
    private val hookedClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    @JvmStatic
    fun setupHooks(classLoader: ClassLoader) {
        if (isHooked) return
        isHooked = true

        Log.capture(TAG, "正在初始化网络拦截模块...")

        // 确保配置已加载 (支付宝进程同步)
        try {
            fansirsqi.xposed.sesame.util.DataStore.init(fansirsqi.xposed.sesame.util.Files.CONFIG_DIR)
        } catch (e: Exception) {
            Log.capture(TAG, "NetworkHook 数据存储初始化失败: ${e.message}")
        }
        
        // 1. 拦截标准 HTTP/HTTPS 请求 (HttpURLConnection)
        hookHttpURLConnection()

        // 2. 拦截支付宝 RPC 请求
        hookAlipayRpc(classLoader)
    }

    private fun hookHttpURLConnection() {
        try {
            XposedHelpers.findAndHookMethod(URL::class.java, "openConnection", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val conn = param.result as? HttpURLConnection ?: return
                    val url = conn.url.toString()
                    val host = try { java.net.URI(url).host ?: "" } catch (e: Exception) { "" }

                    // --- 动态域名过滤 ---
                    val filterKeywords = fansirsqi.xposed.sesame.model.BaseModel.httpCaptureFilter.value
                    if (!filterKeywords.isNullOrBlank()) {
                        val keywords = filterKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val match = keywords.find { host.contains(it, ignoreCase = true) || url.contains(it, ignoreCase = true) }
                        if (match != null) {
                            //命中黑名单
                            return
                        }
                    }

                    // 在连接对象上动态添加 Hook
                    hookConnectionInstance(conn)
                }
            })
        } catch (t: Throwable) {
            Log.capture(TAG, "Hook HttpURLConnection 失败: ${t.message}")
        }
    }

    private fun hookConnectionInstance(conn: HttpURLConnection) {
        val clazz = conn.javaClass
        if (!hookedClasses.add(clazz)) return

        try {
            XposedHelpers.findAndHookMethod(clazz, "getResponseCode", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // 占位
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    // 占位
                }
            })
        } catch (t: Throwable) {
            // 忽略混淆导致的 hook 失败
        }
    }

    private fun hookAlipayRpc(classLoader: ClassLoader) {
        try {
            val rpcHandlerClass = XposedHelpers.findClassIfExists("com.alipay.mobile.common.rpc.RpcInvocationHandler", classLoader) ?: return

            XposedHelpers.findAndHookMethod(rpcHandlerClass, "invoke", Any::class.java, Method::class.java, Array<Any>::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val method = param.args[1] as Method
                    
                    val operationTypeAnnClass = XposedHelpers.findClassIfExists("com.alipay.mobile.framework.service.annotation.OperationType", classLoader)
                    val opType = if (operationTypeAnnClass != null) {
                        val ann = method.getAnnotation(operationTypeAnnClass as Class<out Annotation>)
                        if (ann != null) XposedHelpers.callMethod(ann, "value") as? String ?: "" else ""
                    } else ""

                    if (opType.isNotEmpty()) {
                        XposedHelpers.setAdditionalInstanceField(param, "opType", opType)
                        
                        // --- 动态过滤 (RPC 链路) ---
                        val filterKeywords = fansirsqi.xposed.sesame.model.BaseModel.httpCaptureFilter.value
                        if (!filterKeywords.isNullOrBlank()) {
                            val keywords = filterKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            if (keywords.any { opType.contains(it, ignoreCase = true) }) {
                                XposedHelpers.setAdditionalInstanceField(param, "rpc_skip", true)
                                return
                            }
                        }
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (XposedHelpers.getAdditionalInstanceField(param, "rpc_skip") == true) return
                    // 响应处理占位
                }
            })
        } catch (t: Throwable) {
            Log.capture(TAG, "Hook RPC 失败: ${t.message}")
        }
    }
}
