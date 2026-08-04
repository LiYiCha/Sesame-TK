package fansirsqi.xposed.sesame.ui.extra.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.ui.extra.Callbacks
import fansirsqi.xposed.sesame.ui.extra.viewmodel.RpcDebugViewModel

/**
 * 请求列表屏幕（重设计）
 *
 * @param vm ViewModel
 * @param callbacks 回调
 */
@Composable
fun RequestListScreen(vm: RpcDebugViewModel, callbacks: Callbacks) {
    val items by vm.items.collectAsState()

    if (items.isEmpty()) {
        Text(
            "暂无保存的请求",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "请求列表",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        items.forEach { item ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 标题 + Method
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            // Method badge
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = item.method,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    maxLines = 1
                                )
                            }
                        }

                        // 操作按钮组
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = { callbacks.onSend(item.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(painterResource(R.drawable.ic_post), "发送", modifier = Modifier.size(18.dp), tint = Color.Unspecified)
                            }
                            IconButton(onClick = { callbacks.onDuplicate(item.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(painterResource(R.drawable.ic_copy), "复制", modifier = Modifier.size(18.dp), tint = Color.Unspecified)
                            }
                            IconButton(onClick = { callbacks.onDelete(item.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(painterResource(R.drawable.ic_delete), "删除", modifier = Modifier.size(18.dp), tint = Color.Unspecified)
                            }
                            IconButton(onClick = { callbacks.onEdit(item.id); vm.showEditDialog(item) }, modifier = Modifier.size(32.dp)) {
                                Icon(painterResource(R.drawable.ic_edit), "编辑", modifier = Modifier.size(18.dp), tint = Color.Unspecified)
                            }
                            IconButton(onClick = { vm.toggleExpand(item.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    painterResource(if (item.expanded) R.drawable.ic_collapse else R.drawable.ic_expand),
                                    if (item.expanded) "收起" else "展开",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }

                    // 描述
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // 展开的 Data
                    AnimatedVisibility(
                        visible = item.expanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        ) {
                            Text(
                                text = item.data,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!item.expanded) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
