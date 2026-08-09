package fansirsqi.xposed.sesame.ui.theme.app

import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PerformanceMonitor() {
    var fps by remember { mutableStateOf(60) }
    var memoryUsage by remember { mutableStateOf(0f) }

    // FPS 计算逻辑
    DisposableEffect(Unit) {
        var frameCount = 0
        var lastTime = System.nanoTime()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameCount++
                val now = System.nanoTime()
                if (now - lastTime >= 1_000_000_000) { // 1秒
                    fps = frameCount
                    frameCount = 0
                    lastTime = now
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback)
        onDispose { Choreographer.getInstance().removeFrameCallback(callback) }
    }

    // 内存计算逻辑
    LaunchedEffect(Unit) {
        while (true) {
            val runtime = Runtime.getRuntime()
            val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
            memoryUsage = usedMemInMB
            delay(1000) // 每秒更新一次内存
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp, end = 16.dp), // 留出状态栏高度
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "FPS: $fps",
                color = if (fps >= 55) Color.Green else if (fps >= 30) Color.Yellow else Color.Red,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "RAM: ${"%.1f".format(memoryUsage)} MB",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
