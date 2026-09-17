package fansirsqi.xposed.sesame.ui.theme.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.ui.theme.ThemeInfo
import fansirsqi.xposed.sesame.ui.theme.ThemeOperation

/**
 * 主题操作卡片（保留供其他页面使用）
 */
@Composable
fun OperationsCard(
    onExecute: (ThemeOperation) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "主题操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OperationRow(
                operation = ThemeOperation.EXPORT,
                icon = { Icon(Icons.Default.Save, null, Modifier.size(16.dp)) },
                onExecute = { onExecute(ThemeOperation.EXPORT) }
            )

            OperationRow(
                operation = ThemeOperation.DELETE,
                icon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) },
                onExecute = { onExecute(ThemeOperation.DELETE) }
            )

            OperationRow(
                operation = ThemeOperation.UPDATE,
                icon = { Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)) },
                onExecute = { onExecute(ThemeOperation.UPDATE) }
            )

            Text(
                text = "操作将通过广播立即在支付宝进程执行",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单行操作按钮
 */
@Composable
private fun OperationRow(
    operation: ThemeOperation,
    icon: @Composable () -> Unit,
    onExecute: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExecute)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(
                text = operation.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 主题信息卡片（供详情页使用）
 */
@Composable
fun ThemeInfoCard(theme: ThemeInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (theme.isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "当前主题",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = theme.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
