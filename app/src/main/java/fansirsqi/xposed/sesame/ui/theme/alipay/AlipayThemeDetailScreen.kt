package fansirsqi.xposed.sesame.ui.theme.alipay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.util.FolderLauncher

/**
 * 主题包详情：只呈现内容，操作由容器底栏承载
 */
@Composable
fun AlipayThemeDetailScreen(packId: String, viewModel: AlipayThemeViewModel) {
    val state by viewModel.state.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val pack = state.availablePacks.firstOrNull { it.packId == packId }
    val context = LocalContext.current

    // 预览模式默认跟随 App 当前夜间模式（与 SesameTheme 的判定同源）
    val systemIsDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(systemIsDark) }
    var showInventory by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(packId) { viewModel.loadDetail(packId) }

    val slots = detail.slots
    var page by rememberSaveable(packId) { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable(packId) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (pack != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pack.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            detail.isLoading -> AlipayPackSkeleton()

            slots == null -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "读取主题资源失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "下拉刷新或从底栏重新进入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                val variants = slots.navVariants(dark)
                val activePage = page?.takeIf { picked -> variants.any { it.page == picked } }
                    ?: variants.firstOrNull()?.page.orEmpty()
                val activeTab = selectedTab ?: slots.tabKeys.firstOrNull().orEmpty()

                ModeToggle(
                    variants = variants,
                    page = activePage,
                    dark = dark,
                    onPage = { page = it },
                    onDark = { dark = it }
                )

                AlipayThemePhoneMock(
                    slots = slots,
                    page = activePage,
                    dark = dark,
                    selectedTab = activeTab,
                    compact = false,
                    onTabClick = { selectedTab = it },
                    modifier = Modifier.fillMaxWidth()
                )

                variants.firstOrNull { it.page == activePage }
                    ?.sharedPages
                    ?.drop(1)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { shared ->
                        Text(
                            text = shared.joinToString("、") { slots.navLabel(it) } + " 共用当前素材",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                AlipayCompletenessRow(
                    total = slots.total,
                    darkVariant = slots.darkVariant,
                    missing = slots.missing,
                    compact = false
                )

                slots.payment?.takeIf { it.available }?.let { payment ->
                    Text(
                        text = "付款码",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AlipayPaymentCodeMock(payment = payment)
                }

                InventorySection(
                    slots = slots,
                    dark = dark,
                    expanded = showInventory,
                    onToggle = { showInventory = !showInventory }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { FolderLauncher.open(context, pack?.storagePath ?: packId) }
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "存储位置 · 点按打开",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = pack?.storagePath ?: packId,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun InventorySection(
    slots: AlipayThemeSlots,
    dark: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceVariant,
        contentColor = scheme.onSurfaceVariant
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "槽位清单 · ${slots.total}" +
                        if (slots.missing > 0) " · 缺失 ${slots.missing}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(20.dp),
                    tint = scheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    slots.colorSlots.takeIf { it.isNotEmpty() }?.let { colors ->
                        Text(
                            text = "颜色 · ${colors.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary
                        )
                        AlipayColorBand(slots = slots, dark = dark)
                    }
                    slots.uniqueImages.takeIf { it.isNotEmpty() }?.let { images ->
                        Text(
                            text = "图片 · ${images.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary
                        )
                        AlipayImageGrid(slots = slots, dark = dark)
                    }
                    AlipayMissingLine(slots = slots)
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(
    variants: List<NavVariant>,
    page: String,
    dark: Boolean,
    onPage: (String) -> Unit,
    onDark: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (variants.size > 1) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(count = variants.size, key = { variants[it].page }) { index ->
                    val variant = variants[index]
                    FilterChip(
                        selected = variant.page == page,
                        onClick = { onPage(variant.page) },
                        label = { Text(variant.label) }
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        FilterChip(
            selected = dark,
            onClick = { onDark(!dark) },
            label = { Text(if (dark) "暗色" else "亮色") }
        )
    }
}

