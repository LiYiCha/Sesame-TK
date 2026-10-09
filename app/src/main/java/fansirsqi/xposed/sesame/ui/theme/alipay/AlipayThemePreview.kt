package fansirsqi.xposed.sesame.ui.theme.alipay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import androidx.core.graphics.toColorInt

/**
 * 实景 mock：外层容器一律用 App 全局 token，只有 mock 内部的底色与文字色取自主题包槽位数据
 */
@Composable
fun AlipayThemePhoneMock(
    slots: AlipayThemeSlots,
    page: String,
    dark: Boolean,
    selectedTab: String,
    compact: Boolean,
    onTabClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        // mock 外壳恒用 App 表面色，dark 只决定取包内哪一套素材
        color = scheme.surface,
        contentColor = scheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            NavHeader(
                slots = slots,
                page = page,
                dark = dark,
                height = if (compact) 44.dp else 92.dp,
                compact = compact
            )

            if (!compact) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    slots.actionSlots.forEach { slot ->
                        ActionEntry(slot = slot, file = slots.file(slot.position, dark))
                    }
                }
            }

            TabBar(
                slots = slots,
                dark = dark,
                selectedTab = selectedTab,
                onTabClick = onTabClick,
                compact = compact
            )
        }
    }
}

@Composable
private fun NavHeader(
    slots: AlipayThemeSlots,
    page: String,
    dark: Boolean,
    height: Dp,
    compact: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val bgPosition = slots.navPosition(page, "bg")
    val bg = slots.file(bgPosition, dark)
    val maskHex = slots.colorHex(slots.navPosition(page, "mask"), dark)
    val titleHex = slots.colorHex(slots.navPosition(page, "theme_color"), dark)
    val fgHex = slots.colorHex(slots.navPosition(page, "theme_fg_color"), dark)

    // 只有确实拿到背景图/蒙层时才叠数据色，否则用中性 token 底，避免文字与底色同为数据色
    val hasDataBackground = bg != null || maskHex != null
    val titleColor = if (hasDataBackground) slotColor(fgHex ?: titleHex, scheme.onSurface) else scheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                if (!hasDataBackground) scheme.surfaceVariant
                else slotColor(maskHex ?: titleHex, scheme.surfaceVariant)
            )
    ) {
        if (bg != null) {
            AsyncImage(
                model = bg,
                contentDescription = bgPosition,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!compact) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = bgPosition,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (maskHex != null) {
            val mask = slotColor(maskHex, Color.Transparent)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(mask.copy(alpha = 0f), mask)))
            )
        }

        if (!compact) {
            Text(
                text = slots.slot(bgPosition)?.label ?: page,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        }
    }
}

@Composable
private fun ActionEntry(slot: ThemeSlot, file: File?) {
    val scheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier.size(if (file != null) 30.dp else 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = slot.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = slot.label,
                        modifier = Modifier.size(12.dp),
                        tint = scheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = slot.label.shorten(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (file != null) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TabBar(
    slots: AlipayThemeSlots,
    dark: Boolean,
    selectedTab: String,
    onTabClick: (String) -> Unit,
    compact: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val bgFile = slots.file("tab_bar_bg", dark)
    val themeHex = slots.colorHex("tab_bar_theme_color", dark)
    val normalHex = slots.colorHex("tab_bar_text_color_normal", dark)
    val selectedHex = slots.colorHex("tab_bar_text_color_selected", dark)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 34.dp else 54.dp)
            .background(slotColor(themeHex, scheme.surfaceContainerHigh))
    ) {
        if (bgFile != null) {
            AsyncImage(
                model = bgFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            slots.tabKeys.forEach { key ->
                val selected = key == selectedTab
                val icon = slots.tabIcon(key, selected, dark)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !compact) { onTabClick(key) }
                        .padding(horizontal = 6.dp, vertical = if (compact) 2.dp else 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (icon != null) {
                        AsyncImage(
                            model = icon,
                            contentDescription = slots.tabLabel(key),
                            modifier = Modifier.size(if (compact) 16.dp else 24.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(if (compact) 16.dp else 22.dp)
                                .clip(CircleShape)
                                // 占位符不是包内素材，一律用 token，保证 onPrimary 配对可读
                                .background(
                                    if (selected) scheme.primary else scheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = slots.tabLabel(key).firstOrNull()?.toString() ?: "?",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!compact) {
                        Text(
                            text = slots.tabLabel(key),
                            style = MaterialTheme.typography.labelSmall,
                            color = slotColor(
                                if (selected) selectedHex ?: normalHex else normalHex,
                                if (selected) scheme.primary else scheme.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlipayPaymentCodeMock(payment: PaymentCodeSlots, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        slotColor(payment.gradientStartHex, scheme.primaryContainer),
                        slotColor(payment.gradientEndHex, scheme.surfaceContainerHigh)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        payment.backgroundFile?.let {
            AsyncImage(
                model = it,
                contentDescription = payment.description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        payment.logoFile?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit
            )
        }
        if (!payment.available) {
            Text(
                text = "未提供付款码资源",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 槽位色值只存在于主题包数据中；解析失败或缺失时回退 App 全局 token，源码不写死颜色
 */
@Composable
fun slotColor(hex: String?, fallback: Color): Color = hex?.let { parseHexColor(it) } ?: fallback

private fun parseHexColor(hex: String): Color? =
    runCatching { Color(hex.trim().toColorInt()) }.getOrNull()

private fun String.shorten(): String =
    removeSuffix("icon").removeSuffix("图标").trim().take(4)
