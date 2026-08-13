package fansirsqi.xposed.sesame.ui.skin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 皮肤选择卡片
 *
 * 显示可用皮肤列表，支持预览和选择
 *
 * @param availableSkins 可用皮肤列表
 * @param selectedSkinName 当前选中的皮肤名称
 * @param onSkinSelected 皮肤选择回调
 * @param onRefresh 刷新皮肤列表回调
 * @param onImport 导入皮肤ZIP回调
 * @param onImportDirectory 导入皮肤目录回调
 * @param onViewDetail 查看皮肤详情回调
 */
@Composable
fun SkinSelectorCard(
    availableSkins: List<SkinInfo>,
    selectedSkinName: String?,
    onSkinSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onImport: () -> Unit,
    onImportDirectory: () -> Unit,
    onViewDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择皮肤",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface // 深蓝色，清晰可见
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 导入ZIP按钮
                    FilledTonalButton(
                        onClick = onImport,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary, // 鲜明的青色
                            contentColor = MaterialTheme.colorScheme.onPrimary // 白色文字
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入ZIP", fontWeight = FontWeight.Medium)
                    }

                    // 导入目录按钮
                    FilledTonalButton(
                        onClick = onImportDirectory,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary, // 鲜明的青色
                            contentColor = MaterialTheme.colorScheme.onPrimary // 白色文字
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入目录", fontWeight = FontWeight.Medium)
                    }

                    TextButton(
                        onClick = onRefresh,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary // 鲜明的青色
                        )
                    ) {
                        Text("刷新", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 皮肤列表容器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant) // 浅灰色背景，区分容器
                    .padding(12.dp)
            ) {
                if (availableSkins.isEmpty()) {
                    // 空状态
                    EmptySkinState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(availableSkins) { skin ->
                            SkinItem(
                                skin = skin,
                                isSelected = skin.name == selectedSkinName,
                                onClick = { onSkinSelected(skin.name) },
                                onViewDetail = { onViewDetail(skin.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个皮肤项
 */
@Composable
private fun SkinItem(
    skin: SkinInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onViewDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 选中状态使用鲜明的青色，未选中使用浅灰色
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary // 鲜明的青色
    } else {
        MaterialTheme.colorScheme.outline // 浅灰色
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer // 浅青色背景
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 3.dp else 1.dp, // 选中时更粗的边框
            color = borderColor
        ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp // 选中时有阴影
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 预览图
                SkinPreviewImage(
                    previewPath = skin.previewImagePath,
                    modifier = Modifier
                        .size(80.dp, 50.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                // 皮肤信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = skin.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )

                    if (skin.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = skin.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // 主题色指示器
                    if (skin.themeColor.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeColorIndicator(themeColor = skin.themeColor)
                    }
                }

                // 选中指示器
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已选中",
                        tint = MaterialTheme.colorScheme.primary, // 鲜明的青色
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterVertically)
                    )
                }
            }

            // 查看详情按钮
            TextButton(
                onClick = onViewDetail,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary // 鲜明的青色
                )
            ) {
                Text(
                    text = "查看详情",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

        }
    }
}

/**
 * 皮肤预览图
 */
@Composable
private fun SkinPreviewImage(
    previewPath: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1A1A1A),
                    Color(0xFF2A2A2A)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (previewPath != null) {
            AsyncImage(
                model = previewPath,
                contentDescription = "皮肤预览",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "无预览图",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * 主题色指示器
 */
@Composable
private fun ThemeColorIndicator(
    themeColor: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(parseColor(themeColor))
        )
        Text(
            text = themeColor,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 空状态显示
 */
@Composable
private fun EmptySkinState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "暂无可用皮肤",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "请先下载资源包或更新皮肤缓存",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * 解析颜色字符串
 */
private fun parseColor(colorString: String): Color {
    return try {
        val cleanColor = colorString.removePrefix("#")
        val colorInt = cleanColor.toLong(16)
        Color(colorInt or 0xFF000000)
    } catch (e: Exception) {
        Color.Gray
    }
}
