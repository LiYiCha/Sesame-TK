package fansirsqi.xposed.sesame

import android.app.Application
import fansirsqi.xposed.sesame.data.ViewAppInfo

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局初始化 ViewAppInfo，包含 MMKVUtil 和 DataStore 的初始化，
        // 确保无论从任何组件（包括 RpcDebugActivity）直接拉起，MMKV 均已初始化完毕。
        ViewAppInfo.init(this)
        fansirsqi.xposed.sesame.ui.theme.app.HolidayTheme.applyGlobalNightMode()
    }
}
