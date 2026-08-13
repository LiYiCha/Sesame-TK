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
    protected val toolbar: MaterialToolbar?
        get() {
            return try {
                findViewById(R.id.x_toolbar)
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
        val isSystemNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        // 单一数据源：统一调色板，XML 端与 Compose 端一致
        val palette = HolidayTheme.resolvePalette(isSystemNight)
        val isDark = palette.isDark
        val mainColorInt = palette.primary.toArgb()
        val bgColorInt = palette.background.toArgb()
        val surfaceColorInt = palette.surface.toArgb()
        val surfaceVariantInt = palette.surfaceVariant.toArgb()
        val textColorInt = palette.onSurface.toArgb()
        
        window.decorView.setBackgroundColor(bgColorInt)
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        if (root != null && root.childCount > 0) {
            val contentView = root.getChildAt(0)
            contentView.setBackgroundColor(bgColorInt)
            applyThemeToViews(contentView, isDark, mainColorInt, bgColorInt, surfaceColorInt, surfaceVariantInt, textColorInt)
        }
        
        // 委托给专业的 updateToolbarTheme 处理标题栏
        updateToolbarTheme()
    }

    private fun applyThemeToViews(view: android.view.View, isNightMode: Boolean, mainColorInt: Int, bgColorInt: Int, surfaceColorInt: Int, surfaceVariantInt: Int, textColorInt: Int) {
        try {
            val defaultColorPrimary = androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary)
            val defaultF5F5F5 = android.graphics.Color.parseColor("#F5F5F5")
            val defaultBackground = androidx.core.content.ContextCompat.getColor(this, R.color.background)
            
            // 1. 按钮 (Button / MaterialButton) 染色
            if (view is android.widget.Button) {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(surfaceVariantInt)
                view.setTextColor(android.content.res.ColorStateList.valueOf(textColorInt))
                if (view is com.google.android.material.button.MaterialButton) {
                    view.backgroundTintMode = android.graphics.PorterDuff.Mode.SRC_IN
                }
            }
            
            // 2. WebView 设为透明以露出底色
            if (view is android.webkit.WebView) {
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            
            // 3. 替换 CardView 颜色
            if (view is androidx.cardview.widget.CardView) {
                view.setCardBackgroundColor(surfaceColorInt)
            }
            
            // 4. 替换文字主色调
            if (view is android.widget.TextView && view !is android.widget.Button) {
                if (view.currentTextColor == defaultColorPrimary) {
                    view.setTextColor(mainColorInt)
                }
            }
            
            // 5. 修复硬编码的死板背景 (如 #F5F5F5 或 @color/background)
            if (view.background is android.graphics.drawable.ColorDrawable) {
                val bg = view.background as android.graphics.drawable.ColorDrawable
                if (bg.color == defaultF5F5F5 || bg.color == defaultBackground) {
                    // 对非根部的特定布局给一个融入主题的柔和区块颜色，否则透明化露出被我们染色的 contentView 底色
                    if (view.id != android.view.View.NO_ID && view.id != android.R.id.content) {
                        view.setBackgroundColor(surfaceVariantInt)
                    } else {
                        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                }
            }
            
            // 6. 递归遍历子节点
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyThemeToViews(view.getChildAt(i), isNightMode, mainColorInt, bgColorInt, surfaceColorInt, surfaceVariantInt, textColorInt)
                }
            }
        } catch (e: Exception) {
            fansirsqi.xposed.sesame.util.Log.printStackTrace(e)
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

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        themeObserver.invoke()
    }

    override fun setContentView(view: android.view.View?) {
        super.setContentView(view)
        themeObserver.invoke()
    }

    override fun setContentView(view: android.view.View?, params: android.view.ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        themeObserver.invoke()
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
        val isSystemNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val palette = HolidayTheme.resolvePalette(isSystemNight)
        val isNightMode = palette.isDark
        
        // 标题栏背景 = surface（与卡片一致），渐变到 background（与页面一致）
        val startColor = palette.surface.toArgb()
        val endColor = palette.background.toArgb()
        val textColor = palette.onSurface.toArgb()
        
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
        
        tb.setTitleTextColor(textColor)
        tb.setSubtitleTextColor(textColor)
        
        // 菜单按钮（overflow "..."）与导航图标颜色适配深浅主题
        try {
            tb.overflowIcon?.setTint(textColor)
            tb.navigationIcon?.setTint(textColor)
        } catch (_: Exception) {}
        
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode
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
