package com.updater.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 主题注入点（依赖倒置）：
 * 宿主可在初始化时提供自己的 ColorScheme 工厂（如 SesameTheme/HolidayTheme 换肤体系），
 * 更新模块所有 Compose 页面将跟随宿主主题；未注入时回落到系统动态取色/标准 M3 配色。
 *
 * provider 在组合期调用——宿主实现中读取任何 Compose 状态（如 themeVersion）即可
 * 让模块页面在宿主换肤时自动重组同步。
 */
object UpdaterThemeConfig {

    @Volatile
    var schemeProvider: (@Composable (dark: Boolean) -> ColorScheme?)? = null
}

/**
 * 更新模块统一 Compose 主题：
 * 1. 宿主注入的 ColorScheme（优先，与主模块完全同步，含换肤）
 * 2. Android 12+ 系统动态取色（未注入时的兜底）
 * 3. 低版本标准 M3 配色
 */
@Composable
fun UpdaterComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()

    val provider = UpdaterThemeConfig.schemeProvider
    val injected = provider?.invoke(dark)

    val scheme = injected ?: when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
