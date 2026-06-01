package fansirsqi.xposed.sesame.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.data.RunType.*
import fansirsqi.xposed.sesame.data.ViewAppInfo
import fansirsqi.xposed.sesame.util.PermissionUtil
import fansirsqi.xposed.sesame.ui.theme.app.HolidayTheme
import androidx.compose.ui.graphics.toArgb

open class BaseActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_EXTERNAL_STORAGE = 1
    }
    // Toolbar 安全获取：兼容 Compose 模式
    protected val toolbar: MaterialToolbar? by lazy { 
        try {
            findViewById<MaterialToolbar>(R.id.x_toolbar)
        } catch (e: Exception) {
            null
        }
    }

    // 基础标题
    open var baseTitle: String?
        get() = ViewAppInfo.appTitle
        set(value) {
            toolbar?.title = value
        }

    // 基础副标题
    open var baseSubtitle: String?
        get() = null
        set(value) {
            toolbar?.subtitle = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PermissionUtil.checkFilePermissions(this)) {
            initialize()
        } else {
            PermissionUtil.checkOrRequestFilePermissions(this)
            ViewAppInfo.init(applicationContext)
        }
    }

    private fun initialize() {
        ViewAppInfo.init(applicationContext)
        // Edge-to-Edge 支持
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 控制状态栏文字颜色
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initialize()
            } else {
                Toast.makeText(this, "未获取文件读写权限", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onContentChanged() {
        super.onContentChanged()
        
        // 只有当存在传统的 Toolbar 时才执行这些逻辑
        toolbar?.let { tb ->
            setSupportActionBar(tb)

            val holidayColors = HolidayTheme.getHolidayColors()
            val isLight = if (holidayColors != null) {
                val argbColor = holidayColors.mainColor.toArgb()
                tb.setBackgroundColor(argbColor)
                
                val r = android.graphics.Color.red(argbColor) / 255f
                val g = android.graphics.Color.green(argbColor) / 255f
                val b = android.graphics.Color.blue(argbColor) / 255f
                val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val light = luminance > 0.6f
                
                if (ViewAppInfo.getRunType() == DISABLE) {
                    setBaseTitleTextColor(ContextCompat.getColor(this, R.color.not_active_text))
                } else {
                    val textColor = if (light) android.graphics.Color.parseColor("#1A1A1A") else android.graphics.Color.WHITE
                    tb.setTitleTextColor(textColor)
                    tb.setSubtitleTextColor(textColor)
                }
                light
            } else {
                when (ViewAppInfo.getRunType()) {
                    DISABLE -> setBaseTitleTextColor(
                        ContextCompat.getColor(this, R.color.not_active_text)
                    )
                    ACTIVE, LOADED -> setBaseTitleTextColor(Color.WHITE)
                    else -> setBaseTitleTextColor(Color.WHITE)
                }
                false
            }
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = isLight
            // 文字居中显示，MaterialToolbar 会自动处理状态栏高度
            tb.setContentInsetsAbsolute(0, 0)
            tb.title = baseTitle
            tb.subtitle = baseSubtitle
        }
    }

    fun setBaseTitleTextColor(color: Int) {
        toolbar?.setTitleTextColor(color)
    }

    override fun attachBaseContext(newBase: Context) {
        val configurationNew = Configuration(newBase.resources.configuration)
        val context = newBase.createConfigurationContext(configurationNew)
        super.attachBaseContext(context)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 夜间模式变化时刷新 Activity
        if ((newConfig.diff(resources.configuration) and Configuration.UI_MODE_NIGHT_MASK) != 0) {
            recreate()
        } else {
            toolbar?.let { it.title = baseTitle }
            toolbar?.let { it.subtitle = baseSubtitle }
        }
    }
}
