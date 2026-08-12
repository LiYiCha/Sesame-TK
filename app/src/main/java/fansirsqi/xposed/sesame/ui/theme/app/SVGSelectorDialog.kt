package fansirsqi.xposed.sesame.ui.theme.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

@Composable
fun SVGSelectorDialog(
    type: String, // "lines", "animal", or "plant"
    onDismissRequest: () -> Unit
) {
    val items = when (type) {
        "lines" -> EcosystemManager.allLines
        "animal" -> EcosystemManager.allAnimals
        "plants" -> EcosystemManager.allPlants
        else -> emptyList()
    }
    
    val title = when (type) {
        "lines" -> "选择极简线条"
        "animal" -> "选择陪伴精灵"
        "plants" -> "选择植物精灵"
        else -> "选择资源"
    }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("当前库为空\n(请确保资源未因乱码而丢失)", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { fileName ->
                        val url = "file:///android_asset/ecosystem/$type/$fileName"
                        val isSelected = when (type) {
                            "lines" -> EcosystemManager.currentLineIcon == url
                            "animal" -> EcosystemManager.currentAnimal == url
                            "plants" -> EcosystemManager.currentAnimal == url
                            else -> false
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    when (type) {
                                        "lines" -> EcosystemManager.currentLineIcon = url
                                        "animal" -> EcosystemManager.currentAnimal = url
                                        "plants" -> EcosystemManager.currentAnimal = url
                                    }
                                    onDismissRequest()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .apply {
                                        if (url.endsWith(".svg", ignoreCase = true)) {
                                            decoderFactory(SvgDecoder.Factory())
                                        }
                                    }
                                    .build(),
                                contentDescription = fileName,
                                modifier = Modifier.size(32.dp),
                                colorFilter = if (type == "lines") androidx.compose.ui.graphics.ColorFilter.tint(if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) else null
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭")
            }
        }
    )
}
