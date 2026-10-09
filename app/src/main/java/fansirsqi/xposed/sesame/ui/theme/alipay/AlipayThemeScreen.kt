package fansirsqi.xposed.sesame.ui.theme.alipay

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.util.FolderLauncher
import fansirsqi.xposed.sesame.util.ToastUtil

/**
 * 主题中心容器：操作统一收在固定底栏，顶栏只留返回与低频菜单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlipayThemeScreen(viewModel: AlipayThemeViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val context = LocalContext.current

    var openedPackId by rememberSaveable { mutableStateOf<String?>(null) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var pendingDeletePackId by rememberSaveable { mutableStateOf<String?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importFromZip)
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importFromDirectory)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val notice = event as? AlipayThemeEvent.Notice ?: return@collect
            ToastUtil.showToast(context, notice.text)
        }
    }

    val inList = openedPackId == null
    val openedPack = state.availablePacks.firstOrNull { it.packId == openedPackId }

    BackHandler(enabled = !inList) { openedPackId = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (inList) "主题中心" else "主题详情") },
                    navigationIcon = {
                        IconButton(onClick = { if (inList) onBack() else openedPackId = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        if (inList) {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("刷新") },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.refresh()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("打开主题目录") },
                                    onClick = {
                                        showMoreMenu = false
                                        FolderLauncher.open(context, viewModel.themesRoot())
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(AlipayThemeOperation.UPDATE.displayName) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.execute(AlipayThemeOperation.UPDATE)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(AlipayThemeOperation.DELETE.displayName) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.execute(AlipayThemeOperation.DELETE)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (state.isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                // Scaffold 的 bottomBar 槽位不自动避让手势条，这里显式消费 insets
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (inList) {
                        OutlinedButton(
                            onClick = { viewModel.execute(AlipayThemeOperation.EXPORT) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(AlipayThemeOperation.EXPORT.displayName)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { showImportMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("导入")
                            }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导入 ZIP") },
                                    onClick = {
                                        showImportMenu = false
                                        zipPicker.launch("application/zip")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导入目录") },
                                    onClick = {
                                        showImportMenu = false
                                        treePicker.launch(null)
                                    }
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { openedPackId?.let(viewModel::applyPack) },
                            enabled = !detail.applying && detail.slots != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (detail.applying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = if (openedPack?.isSelected == true) "重新应用" else "应用",
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        TextButton(onClick = { pendingDeletePackId = openedPackId }) {
                            Text(
                                text = "删除",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = openedPackId,
            transitionSpec = {
                val forward = targetState != null
                val direction = if (forward) 1 else -1
                (slideInHorizontally(tween(220)) { width -> direction * width } + fadeIn(tween(220)))
                    .togetherWith(
                        slideOutHorizontally(tween(220)) { width -> -direction * width } + fadeOut(tween(220))
                    )
            },
            label = "alipayThemeRoute"
        ) { packId ->
            Box(modifier = Modifier.padding(padding)) {
                if (packId == null) {
                    AlipayThemeList(
                        state = state,
                        peekSlots = viewModel::peekSlots,
                        onOpen = { openedPackId = it }
                    )
                } else {
                    AlipayThemeDetailScreen(packId = packId, viewModel = viewModel)
                }
            }
        }
    }

    pendingDeletePackId?.let { packId ->
        val target = state.availablePacks.firstOrNull { it.packId == packId }
        AlertDialog(
            onDismissRequest = { pendingDeletePackId = null },
            title = { Text("删除主题") },
            text = { Text("「${target?.name ?: packId}」目录内文件将全部移除，不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletePackId = null
                        openedPackId = null
                        viewModel.deletePack(packId)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePackId = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AlipayThemeList(
    state: AlipayThemeState,
    peekSlots: (String) -> AlipayThemeSlots?,
    onOpen: (String) -> Unit
) {
    val packs = state.availablePacks

    if (state.isLoading && packs.isEmpty()) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(count = 6, key = { "skeleton_$it" }) { AlipayPackRowSkeleton() }
        }
        return
    }

    if (packs.isEmpty()) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item { AlipayEmptyState() }
        }
        return
    }

    // 使用中的排首位，其余保持扫描顺序
    val ordered = packs.sortedByDescending { it.isSelected }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "section_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部主题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${ordered.size} 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(ordered, key = { it.packId }) { pack ->
            AlipayThemePackRow(
                pack = pack,
                slots = peekSlots(pack.packId),
                onClick = { onOpen(pack.packId) }
            )
        }
    }
}
