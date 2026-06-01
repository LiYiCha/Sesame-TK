package fansirsqi.xposed.sesame.hook.core.modules

import android.content.Context
import fansirsqi.xposed.sesame.hook.core.HookModule
import fansirsqi.xposed.sesame.hook.network.HttpCaptureHook
import fansirsqi.xposed.sesame.hook.network.NetworkHook
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log

class NetworkModule : HookModule {
    private val TAG = "NetworkModule"

    override fun onPostAppAttach(context: Context, classLoader: ClassLoader) {
        // 网络拦截模块不再在此处过早初始化，改由 LifecycleManager 统一管控
    }

    // Note: HttpCaptureHook.setup is called in Service.onCreate in the original code.
    // I will keep it there for now or move it to a more appropriate place if needed.
}
