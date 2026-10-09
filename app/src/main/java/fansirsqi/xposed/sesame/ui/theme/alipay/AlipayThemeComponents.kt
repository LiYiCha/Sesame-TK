package fansirsqi.xposed.sesame.ui.theme.alipay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fansirsqi.xposed.sesame.ui.theme.app.SesameColors
import java.io.File

@Composable
fun AlipayThemePackRow(
    pack: AlipayThemePack,
    slots: AlipayThemeSlots?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val cover = slots?.let { table ->
        table.navVariants(dark).firstOrNull()?.let { table.file(table.navPosition(it.page, "bg"), dark) }
    } ?: pack.coverPath?.let(::File)

    // 容器色与前景色成对取自主题：选中用 primaryContainer/onPrimaryContainer
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pack.isSelected) scheme.primaryContainer else scheme.surface,
            contentColor = if (pack.isSelected) scheme.onPrimaryContainer else scheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (pack.isSelected) BorderStroke(1.dp, scheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = pack.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val themeHex = slots?.colorHex("tab_bar_theme_color", dark)
                    if (themeHex != null) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(slotColor(themeHex, scheme.primary))
                        )
                    }
                    Text(
                        text = if (pack.slotMissing > 0) "${pack.subtitle} · 缺 ${pack.slotMissing}"
                        else pack.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pack.slotMissing > 0) SesameColors.Warning
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (pack.isSelected) {
                Text(
                    text = "使用中",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun AlipayPackRowSkeleton(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.surfaceVariant)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.surfaceVariant)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.25f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.surfaceVariant)
            )
        }
    }
}

@Composable
fun AlipayCompletenessRow(
    total: Int,
    darkVariant: Int,
    missing: Int,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val present = (total - missing).coerceAtLeast(0)
    val ratio = if (total == 0) 0f else present.toFloat() / total

    if (compact) {
        Text(
            text = buildString {
                append("$total 槽位 · 暗色 $darkVariant")
                if (missing > 0) append(" · 缺失 $missing")
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (missing > 0) SesameColors.Warning else scheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (missing == 0) SesameColors.Success else SesameColors.Warning)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "$total 槽位",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            Text(
                text = "暗色 $darkVariant",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            if (missing > 0) {
                Text(
                    text = "缺失 $missing",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SesameColors.Warning
                )
            }
        }
    }
}

@Composable
fun AlipayPackSkeleton(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonLine(width = 0.45f, height = 18.dp)
            SkeletonLine(width = 0.25f, height = 12.dp)
            SkeletonLine(width = 1f, height = 64.dp)
            SkeletonLine(width = 0.6f, height = 10.dp)
        }
    }
}

@Composable
private fun SkeletonLine(width: Float, height: androidx.compose.ui.unit.Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.surfaceVariant)
    )
}

@Composable
fun AlipayLoadingOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun AlipayEmptyState(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceVariant,
            contentColor = scheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = scheme.primary
            )
            Text(
                text = "暂无主题",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface
            )
            Text(
                text = "用下方「导出主题」从支付宝取回已有主题，或「导入」本地 ZIP／目录",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 颜色槽位色带：每行 4 个，亮/暗两块并排自证差异，不再用文字角标
 */
@Composable
fun AlipayColorBand(slots: AlipayThemeSlots, dark: Boolean, modifier: Modifier = Modifier) {
    val colors = slots.colorSlots
    if (colors.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.chunked(4).forEach { rowSlots ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSlots.forEach { slot ->
                    ColorTile(
                        label = slot.label,
                        colorHex = if (dark) slot.darkColorHex ?: slot.colorHex else slot.colorHex,
                        darkColorHex = slot.darkColorHex,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - rowSlots.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ColorTile(label: String, colorHex: String?, darkColorHex: String?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Swatch(colorHex, Modifier.weight(1f))
            if (darkColorHex != null) Swatch(darkColorHex, Modifier.weight(1f))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Swatch(colorHex: String?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(slotColor(colorHex, scheme.surfaceVariant))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
    )
}

/**
 * 图片槽位缩略网格：同一张图只出现一次，右上角标注它服务几个槽位
 */
@Composable
fun AlipayImageGrid(slots: AlipayThemeSlots, dark: Boolean, modifier: Modifier = Modifier) {
    val images = slots.uniqueImages
    if (images.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        images.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { group ->
                    ImageTile(
                        file = if (group.present) File(slots.packDir, group.fileName) else null,
                        label = slots.slot(group.positions.first())?.label ?: group.fileName,
                        shareCount = group.positions.size,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ImageTile(file: File?, label: String, shareCount: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (shareCount > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = scheme.primary.copy(alpha = 0.85f),
                        contentColor = scheme.onPrimary
                    ) {
                        Text(
                            text = "×$shareCount",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = SesameColors.Warning
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlipayMissingLine(slots: AlipayThemeSlots, modifier: Modifier = Modifier) {
    val missing = slots.missingSlots
    Text(
        text = if (missing.isEmpty()) "槽位素材齐备"
        else "缺失 ${missing.size} 项：" + missing.joinToString("、") { it.position },
        style = MaterialTheme.typography.labelSmall,
        color = if (missing.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else SesameColors.Warning,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth()
    )
}
