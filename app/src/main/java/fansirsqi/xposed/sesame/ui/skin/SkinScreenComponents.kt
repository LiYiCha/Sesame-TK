package fansirsqi.xposed.sesame.ui.skin

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 操作按钮组卡片
 *
 * 显示导出、删除、更新等操作按钮，以及启用/禁用皮肤的开关
 */
@Composable
fun OperationsCard(
    operationStates: Map<SkinOperation, Boolean>,
    onToggle: (SkinOperation) -> Unit,
    onExecute: (SkinOperation) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF3FEADB),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "皮肤操作",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF131313)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 启用/禁用皮肤开关（独立显示）
            val isActivated = operationStates[SkinOperation.ACTIVATE] ?: false
            ActivateSkinSwitch(
                isActivated = isActivated,
                onToggle = {
                    onToggle(SkinOperation.ACTIVATE)
                    val message = if (!isActivated) {
                        "已启用自定义皮肤系统"
                    } else {
                        "已禁用自定义皮肤系统"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 操作说明
            Text(
                text = "点击按钮立即创建操作请求，切换到支付宝应用时自动执行",
                fontSize = 12.sp,
                color = Color(0xFF757575)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 操作按钮列表（排除 ACTIVATE）
            SkinOperation.values().filter { it != SkinOperation.ACTIVATE }.forEach { operation ->
                OperationButton(
                    operation = operation,
                    onExecute = {
                        onExecute(operation)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * 启用/禁用皮肤开关
 */
@Composable
private fun ActivateSkinSwitch(
    isActivated: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = if (isActivated) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "启用自定义皮肤",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF131313)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isActivated) "自定义皮肤系统已启用" else "自定义皮肤系统已禁用",
                    fontSize = 12.sp,
                    color = Color(0xFF757575)
                )
            }
        }

        // 开关
        Switch(
            checked = isActivated,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF4CAF50),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFBDBDBD)
            )
        )
    }
}

/**
 * 操作按钮
 */
@Composable
private fun OperationButton(
    operation: SkinOperation,
    onExecute: () -> Unit
) {
    Button(
        onClick = onExecute,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when (operation) {
                SkinOperation.EXPORT -> Color(0xFF2196F3)
                SkinOperation.DELETE -> Color(0xFFF44336)
                SkinOperation.UPDATE -> Color(0xFF4CAF50)
                else -> Color(0xFF9E9E9E)
            },
            contentColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (operation) {
                    SkinOperation.EXPORT -> Icons.Default.FileUpload
                    SkinOperation.DELETE -> Icons.Default.Delete
                    SkinOperation.UPDATE -> Icons.Default.Refresh
                    else -> Icons.Default.Settings
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = operation.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = getOperationDescription(operation),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

/**
 * 获取操作描述
 */
private fun getOperationDescription(operation: SkinOperation): String {
    return when (operation) {
        SkinOperation.EXPORT -> "从支付宝导出当前使用的皮肤到SD卡"
        SkinOperation.DELETE -> "删除支付宝的皮肤缓存，强制重新加载"
        SkinOperation.UPDATE -> "将选中的皮肤推送到支付宝缓存"
        SkinOperation.ACTIVATE -> "启用自定义皮肤系统（必须开启）"
    }
}

/**
 * 下载资源包卡片
 */
@Composable
fun DownloadCard(
    downloadState: DownloadState,
    isResourceInstalled: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color(0xFF2FE7D6),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "资源包管理",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF131313)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 下载按钮
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00BCD4), // 鲜明的青色
                    contentColor = Color.White, // 白色文字，高对比度
                    disabledContainerColor = Color(0xFFE0E0E0),
                    disabledContentColor = Color(0xFF9E9E9E)
                ),
                enabled = downloadState !is DownloadState.Downloading
            ) {
                when (downloadState) {
                    is DownloadState.Idle -> {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isResourceInstalled) "重新下载资源包" else "下载资源包",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is DownloadState.Downloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "下载中 ${downloadState.progress}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is DownloadState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "下载完成",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is DownloadState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "下载失败",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 进度条（下载中显示）
            AnimatedVisibility(
                visible = downloadState is DownloadState.Downloading,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = (downloadState as? DownloadState.Downloading)?.progress?.div(100f) ?: 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF00BCD4), // 鲜明的青色
                        trackColor = Color(0xFFE0E0E0)
                    )
                }
            }

            // 提示信息
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "从 GitHub 下载资源包需要 SD 卡权限",
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E)
            )
        }
    }
}

/**
 * 底部操作区域
 */
@Composable
fun BottomActions(
    isResourceInstalled: Boolean,
    onOpenFolder: () -> Unit,
    onOpenGithub: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 打开资源文件夹按钮
        OutlinedButton(
            onClick = onOpenFolder,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF00BCD4), // 鲜明的青色
                disabledContentColor = Color(0xFFBDBDBD)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = if (isResourceInstalled) Color(0xFF00BCD4) else Color(0xFFE0E0E0)
            ),
            enabled = isResourceInstalled
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "打开资源文件夹",
                fontSize = 15.sp
            )
        }

        // GitHub 链接
        TextButton(
            onClick = onOpenGithub,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color(0xFF00BCD4) // 鲜明的青色
            )
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF2FE7D6)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "查看 GitHub 项目",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 提示信息
        Text(
            text = "重新打开付款码以使更改生效",
            fontSize = 12.sp,
            color = Color(0xFF9E9E9E),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
