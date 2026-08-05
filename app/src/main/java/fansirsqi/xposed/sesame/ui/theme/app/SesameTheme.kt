package fansirsqi.xposed.sesame.ui.theme.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2D5A27), // Deep Forest Green (Original)
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9F5E9), // Mint Green (Original)
    onPrimaryContainer = Color(0xFF2D5A27),
    secondary = Color(0xFF435B71), // Slate Blue-Gray (Not purple)
    onSecondary = Color.White,
    background = SesameColors.Background,
    onBackground = SesameColors.TextMain,
    surface = SesameColors.Surface,
    onSurface = SesameColors.TextMain,
    surfaceVariant = SesameColors.SurfaceVariant,
    onSurfaceVariant = SesameColors.TextSecondary,
    error = SesameColors.Error,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784), // Soft Green (Instead of blue)
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF7CB342), // Light Olive Green (Not purple)
    onSecondary = Color(0xFF1B5E20),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF757575),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun SesameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val mode = HolidayTheme.getDarkMode()
    val resolvedDark = when {
        mode == "light" -> false
        mode == "dark" -> true
        mode == "schedule" -> HolidayTheme.shouldUseDarkTheme()
        else -> darkTheme
    }
    // 调度模式下优先使用时段主题，否则走节日主题
    val holidayScheme = if (mode == "schedule") {
        HolidayTheme.getTimeTheme()?.let { colors ->
            if (resolvedDark) {
                darkColorScheme(
                    primary = colors.activeColor, onPrimary = Color.Black,
                    primaryContainer = colors.mainColor, onPrimaryContainer = Color.White,
                    secondary = colors.activeColor, onSecondary = Color.Black,
                    background = Color(0xFF121212), onBackground = Color(0xFFE0E0E0),
                    surface = Color(0xFF1E1E1E), onSurface = Color(0xFFE0E0E0),
                    surfaceVariant = Color(0xFF2C2C2C), onSurfaceVariant = Color(0xFFBDBDBD)
                )
            } else {
                lightColorScheme(
                    primary = colors.mainColor, onPrimary = Color.White,
                    primaryContainer = colors.bgColor, onPrimaryContainer = colors.mainColor,
                    secondary = colors.activeColor, onSecondary = Color.White,
                    background = colors.bgColor, onBackground = colors.textColor,
                    surface = colors.cardBgColor, onSurface = colors.textColor,
                    surfaceVariant = colors.bgColor, onSurfaceVariant = colors.textColor.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        HolidayTheme.getHolidayColorScheme(resolvedDark)
    }
    val colorScheme = holidayScheme ?: (if (resolvedDark) DarkColorScheme else LightColorScheme)
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
                // 夜晚散星动画（仅深色模式）
                if (resolvedDark) {
                    TwinklingStars(tint = colorScheme.primary.copy(alpha = 0.15f))
                }
            }
        }
    )
}

/** 夜晚散星 — Canvas 绘制随机闪烁星点 */
@Composable
private fun TwinklingStars(tint: Color) {
    data class Star(val x: Float, val y: Float, val period: Float, val radius: Float)
    val stars = remember {
        List(18) {
            Star(
                x = kotlin.random.Random.nextFloat(),
                y = kotlin.random.Random.nextFloat(),
                period = kotlin.random.Random.nextInt(500, 3000).toFloat(),
                radius = (2f + (it % 4)).coerceAtMost(5f)
            )
        }
    }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            time.floatValue += 0.02f
            if (time.floatValue > 1000f) time.floatValue = 0f
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEachIndexed { i, star ->
            val a = (kotlin.math.sin(time.floatValue * 3f + i * 1.7f).toFloat() * 0.5f + 0.5f) * 0.4f
            drawCircle(
                color = tint.copy(alpha = a.coerceIn(0.02f, 0.40f)),
                radius = star.radius,
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }
    }
}
