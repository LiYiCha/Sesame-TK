package fansirsqi.xposed.sesame.ui.theme.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.ui.theme.ThemeInfo
import fansirsqi.xposed.sesame.ui.theme.ThemeOperation

/**
 * 操作卡片
 */
@Composable
fun OperationsCard(
    onExecute: (ThemeOperation) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "主题操作",
                style = MaterialTheme.typography.titleMedium
            )

            // 导出操作
            OperationButton(
                operation = ThemeOperation.EXPORT,
                onExecute = { onExecute(ThemeOperation.EXPORT) }
            )

            // 删除操作
            OperationButton(
                operation = ThemeOperation.DELETE,
                onExecute = { onExecute(ThemeOperation.DELETE) }
            )

            // 更新操作
            OperationButton(
                operation = ThemeOperation.UPDATE,
                onExecute = { onExecute(ThemeOperation.UPDATE) }
            )

            Text(
                text = "注意：操作将通过广播立即在支付宝进程执行",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 操作按钮
 */
@Composable
private fun OperationButton(
    operation: ThemeOperation,
    onExecute: () -> Unit
) {
    Button(
        onClick = onExecute,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(operation.displayName)
    }
}

