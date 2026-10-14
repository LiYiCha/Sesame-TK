package fansirsqi.xposed.sesame.ui.theme.app

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import fansirsqi.xposed.sesame.newutil.MMKVUtil
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
    var allPlants: List<String> = emptyList()

    // 新增：全局生态守护舱开关
    var isEcoEnabled by mutableStateOf(true)

    fun saveEcoEnabled(enabled: Boolean) {
        isEcoEnabled = enabled
        try {
            MMKVUtil.getMMKV("sesame-tk").encode("is_eco_enabled", enabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun initAssets(context: Context) {
        if (initialized) return
        withContext(Dispatchers.IO) {
            try {
                // 读取 asserts 文件夹下的生态资源
                allAnimals = context.assets.list("ecosystem/animal")?.toList() ?: emptyList()
                allLines = context.assets.list("ecosystem/lines")?.toList() ?: emptyList()
                allPlants = context.assets.list("ecosystem/plants")?.toList() ?: emptyList()
                
                if (allAnimals.isNotEmpty()) {
                    currentAnimal = "file:///android_asset/ecosystem/animal/${allAnimals.random()}"
                }
                if (allLines.isNotEmpty()) {
                    currentLineIcon = "file:///android_asset/ecosystem/lines/${allLines.random()}"
                }
                
                try {
                    isEcoEnabled = MMKVUtil.getMMKV("sesame-tk").decodeBool("is_eco_enabled", true)
                } catch (e: Exception) {
                    e.printStackTrace()
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
