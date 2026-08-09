package fansirsqi.xposed.sesame.ui.theme.app

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 生态陪伴系统 - 核心资产与逻辑引擎
 */
object EcosystemManager {
    var initialized by mutableStateOf(false)
    var currentAnimal: String? by mutableStateOf(null)
    var currentLineIcon: String? by mutableStateOf(null)
    var allAnimals: List<String> = emptyList()
    var allLines: List<String> = emptyList()

    suspend fun initAssets(context: Context) {
        if (initialized) return
        withContext(Dispatchers.IO) {
            try {
                // 读取 asserts 文件夹下的生态资源
                allAnimals = context.assets.list("ecosystem/animal")?.toList() ?: emptyList()
                allLines = context.assets.list("ecosystem/lines")?.toList() ?: emptyList()
                
                if (allAnimals.isNotEmpty()) {
                    currentAnimal = "file:///android_asset/ecosystem/animal/${allAnimals.random()}"
                }
                if (allLines.isNotEmpty()) {
                    currentLineIcon = "file:///android_asset/ecosystem/lines/${allLines.random()}"
                }
                initialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 提供给守护舱等特殊页面手动重置精灵
    fun shuffle() {
        if (allAnimals.isNotEmpty()) currentAnimal = "file:///android_asset/ecosystem/animal/${allAnimals.random()}"
        if (allLines.isNotEmpty()) currentLineIcon = "file:///android_asset/ecosystem/lines/${allLines.random()}"
    }
}

/**
 * 全局背景底纹 (用于注入到 SesameTheme 根部)
 */
@Composable
fun EcosystemWatermark() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EcosystemManager.initAssets(context) }
    
    val watermarkAsset = EcosystemManager.currentAnimal ?: return
    
    // 全局只画一个低透明度的植物，极简高级
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(watermarkAsset)
                .decoderFactory(SvgDecoder.Factory())
                .build(),
            contentDescription = "Global Ecosystem Watermark",
            modifier = Modifier
                .size(400.dp)
                .offset(x = 50.dp, y = 50.dp)
                .alpha(0.04f)
                .graphicsLayer { rotationZ = -15f }
        )
    }
}

/**
 * 组件级装饰 (用于放置在卡片、按钮旁边的微缩精灵)
 */
@Composable
fun EcosystemCardDecorator(
    modifier: Modifier = Modifier,
    usePlant: Boolean = false
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EcosystemManager.initAssets(context) }
    
    val assetUrl = EcosystemManager.currentAnimal
    if (assetUrl == null) return

    // 轻柔呼吸微交互
    val scale by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(assetUrl)
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = "Card Decorator",
        modifier = modifier
            .size(60.dp)
            .alpha(0.85f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
    )
}

/**
 * 空状态伴随大图 (用于列表为空时的背景伴随)
 */
@Composable
fun EcosystemEmptyState(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EcosystemManager.initAssets(context) }
    
    val animal = EcosystemManager.currentAnimal ?: return
    
    // 慵懒轻微摇摆
    val rotation by rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(animal)
            .apply { if (animal.endsWith(".svg", ignoreCase = true)) decoderFactory(SvgDecoder.Factory()) }
            .build(),
        contentDescription = "Empty State Companion",
        modifier = modifier
            .size(140.dp)
            .alpha(0.6f)
            .graphicsLayer {
                rotationZ = rotation
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
    )
}
