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
            supportActionBar?.title = value
        }

    // 基础副标题
    open var baseSubtitle: String?
        get() = null
        set(value) {
            toolbar?.subtitle = value
            supportActionBar?.subtitle = value
        }

    private var currentThemeVersion = 0

    private val themeObserver: () -> Unit = {
        val isDark = HolidayTheme.shouldUseDarkTheme()
        val color = if (isDark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.parseColor("#F5F5F5")
        window.decorView.setBackgroundColor(color)
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        if (root.childCount > 0) {
            root.getChildAt(0).setBackgroundColor(color)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        HolidayTheme.applyGlobalNightMode()
        currentThemeVersion = HolidayTheme.themeVersion.intValue
        super.onCreate(savedInstanceState)
        HolidayTheme.themeObservers.add(themeObserver)
        themeObserver.invoke()
        if (PermissionUtil.checkFilePermissions(this)) {
            initialize()
        } else {
            PermissionUtil.checkOrRequestFilePermissions(this)
            ViewAppInfo.init(applicationContext)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        HolidayTheme.themeObservers.remove(themeObserver)
    }

    override fun onResume() {
        super.onResume()
        updateToolbarTheme()
        toolbar?.let { tb ->
            tb.title = baseTitle
            supportActionBar?.title = baseTitle
            tb.subtitle = baseSubtitle
            supportActionBar?.subtitle = baseSubtitle
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
            updateToolbarTheme()
            // 文字居中显示，MaterialToolbar 会自动处理状态栏高度
            tb.setContentInsetsAbsolute(0, 0)
            tb.title = baseTitle
            supportActionBar?.title = baseTitle
            tb.subtitle = baseSubtitle
            supportActionBar?.subtitle = baseSubtitle
        }
    }

    fun updateToolbarTheme() {
        val tb = toolbar ?: return
        val mode = HolidayTheme.getDarkMode()
        val isSystemNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isNightMode = when {
            mode == "light" -> false
            mode == "dark" -> true
            mode == "schedule" -> HolidayTheme.shouldUseDarkTheme()
            else -> isSystemNight
        }
        val holidayColors = HolidayTheme.getHolidayColors()
        val argbColor = if (holidayColors != null) {
            holidayColors.bgColor.toArgb() // 使用浅色背景 (淡色)
        } else {
            if (this is WebSettingsActivity) {
                android.graphics.Color.parseColor("#F6F6F6")
            } else {
                ContextCompat.getColor(this, R.color.colorPrimary)
            }
        }
        
        // 1. Calculate gradient colors: from argbColor to a lighter/softer version
        val startColor = if (isNightMode) {
            android.graphics.Color.parseColor("#1A1C1E")
        } else {
            argbColor
        }
        val endColor = if (isNightMode) {
            android.graphics.Color.parseColor("#121212")
        } else {
            android.graphics.Color.argb(
                255,
                (android.graphics.Color.red(startColor) * 0.2f + 255 * 0.8f).toInt(),
                (android.graphics.Color.green(startColor) * 0.2f + 255 * 0.8f).toInt(),
                (android.graphics.Color.blue(startColor) * 0.2f + 255 * 0.8f).toInt()
            )
        }
        
        // 2. Apply gradient to AppBarLayout (which covers the status bar area)
        val appBar = tb.parent as? com.google.android.material.appbar.AppBarLayout
        if (appBar != null) {
            val gradientDrawable = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(startColor, endColor)
            )
            appBar.background = gradientDrawable
            tb.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        } else {
            tb.setBackgroundColor(startColor)
        }
        
        // 3. Set text and status bar colors based on luminance
        val r = android.graphics.Color.red(startColor) / 255f
        val g = android.graphics.Color.green(startColor) / 255f
        val b = android.graphics.Color.blue(startColor) / 255f
        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val light = if (isNightMode) false else luminance > 0.6f
        
        val textColor = if (isNightMode) {
            android.graphics.Color.parseColor("#E0E0E0")
        } else if (holidayColors != null) {
            holidayColors.textColor.toArgb() // 使用主题内置的高对比度深色文本
        } else {
            if (light) android.graphics.Color.parseColor("#1A1A1A") else android.graphics.Color.WHITE
        }
        tb.setTitleTextColor(textColor)
        tb.setSubtitleTextColor(textColor)
        
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = light
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
