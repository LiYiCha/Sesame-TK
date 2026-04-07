package fansirsqi.xposed.sesame.ui.extra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.ui.extra.Callbacks
import fansirsqi.xposed.sesame.ui.extra.viewmodel.RpcDebugViewModel
import fansirsqi.xposed.sesame.R

/**
 * 请求列表屏幕
 *
 * @param vm ViewModel
 * @param callbacks 回调
 */
@Composable
fun RequestListScreen(vm: RpcDebugViewModel, callbacks: Callbacks) {
    val items by vm.items.collectAsState()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // 标题文本
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        /* 操作按钮移至右上角图标显示，保留原按钮代码以供参考：
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { callbacks.onSend(item.id) }) { Text("发送") }
                            Button(onClick = { callbacks.onDuplicate(item.id) }) { Text("复制") }
                            Button(onClick = { callbacks.onDelete(item.id) }) { Text("删除") }
                        }
                        */
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { callbacks.onSend(item.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_post),
                                    contentDescription = "发送",
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                            IconButton(
                                onClick = { callbacks.onDuplicate(item.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_copy),
                                    contentDescription = "复制",
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                            IconButton(
                                onClick = { callbacks.onDelete(item.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_delete),
                                    contentDescription = "删除",
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                            IconButton(
                                onClick = { callbacks.onEdit(item.id); vm.showEditDialog(item) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_edit),
                                    contentDescription = "编辑",
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                            IconButton(
                                onClick = { vm.toggleExpand(item.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painterResource(id = if (item.expanded) R.drawable.ic_collapse else R.drawable.ic_expand),
                                    contentDescription = if (item.expanded) "收起" else "展开",
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Description 文本（如果有）
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    // Method文本
                    Text(
                        text = "Method: ${item.method}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 数据文本
                    if (item.expanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.data,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    /* 操作已移至卡片右上角的图标栏，保留原按钮代码：
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { callbacks.onEdit(item.id); vm.showEditDialog(item) }) { Text("编辑") }
                        Button(onClick = { vm.toggleExpand(item.id) }) { Text(if (item.expanded) "收起" else "展开") }
                    }
                    */
                }
            }
        }
    }
}
