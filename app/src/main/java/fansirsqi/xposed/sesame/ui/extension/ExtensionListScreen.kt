package fansirsqi.xposed.sesame.ui.extension

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 扩展功能列表主屏幕
 *
 * 使用 Jetpack Compose 构建的现代化声明式 UI
 * 采用 Material Design 3 设计规范，提供流畅的用户体验
 *
 * @param viewModel ViewModel 实例
 */
@Composable
fun ExtensionListScreen(viewModel: ExtensionViewModel) {
    // 收集模块状态
    val moduleStates by viewModel.moduleStates.collectAsState()
    val context = LocalContext.current

    // 渐变背景色
    val gradientColors = listOf(
        Color(0xFFF5F7FA),
        Color(0xFFE8EAF6)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = gradientColors
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部标题区域
            ModernTopBar()

            // 模块列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 20.dp)
            ) {
                items(moduleStates) { moduleState ->
                    ModernExtensionCard(
                        moduleState = moduleState,
                        onToggle = { enabled ->
                            viewModel.toggleModule(moduleState.module.id, enabled)
                        },
                        onSettingsClick = {
                            val intent = Intent(context, moduleState.module.activityClass)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 现代化顶部标题栏
 *
 * 使用渐变背景和大标题设计
 */
@Composable
private fun ModernTopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE1D9D2), // RGB 225/217/210
                        Color(0xFFD2FFFB)  // RGB 210/255/251
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column {
            Text(
                text = "扩展功能",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF131313)  
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "管理你的功能模块",
                fontSize = 14.sp,
                color = Color(0xFF424242) // 深灰色
            )
        }
    }
}

/**
 * 现代化扩展模块卡片
 *
 * 采用玻璃态设计，带有阴影和圆角
 * 支持动画效果和交互反馈
 *
 * @param moduleState 模块状态
 * @param onToggle 切换开关的回调
 * @param onSettingsClick 点击设置按钮的回调
 */
@Composable
private fun ModernExtensionCard(
    moduleState: ExtensionModuleState,
    onToggle: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    // 动画状态
    val animatedElevation by animateDpAsState(
        targetValue = if (moduleState.isEnabled) 8.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = animatedElevation,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0xFFD2FFFB).copy(alpha = 0.3f) // 使用新的青色
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 顶部：图标、标题和开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：图标和文字
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 模块图标
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFD2FFFB), // RGB 210/255/251
                                        Color(0xFF2FE7D6)  // 稍深的青色
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = Color(0xFF131313), // 深蓝色图标
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 模块信息
                    Column {
                        Text(
                            text = moduleState.module.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF131313)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = moduleState.module.description,
                            fontSize = 13.sp,
                            color = Color(0xFF616161),
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 右侧：开关（仅当模块有 prefKey 时显示）
                if (moduleState.module.prefKey != null) {
                    Switch(
                        checked = moduleState.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2FE7D6),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFBDBDBD)
                        )
                    )
                }
            }

            // 设置按钮（带动画）
            // 对于有开关的模块，仅在启用时显示；对于无开关的模块，始终显示
            AnimatedVisibility(
                visible = moduleState.module.prefKey == null || moduleState.isEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD2FFFB), // RGB 210/255/251
                            contentColor = Color(0xFF131313) // 深蓝色文字
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (moduleState.module.prefKey != null) "模块设置" else "进入",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
