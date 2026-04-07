package fansirsqi.xposed.sesame.ui.skin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 皮肤设置 Activity
 *
 * 使用 Jetpack Compose 构建的皮肤管理页面
 * 提供会员等级选择、皮肤操作、资源包下载等功能
 *
 * 架构：
 * - Activity: 负责生命周期管理和权限处理
 * - ViewModel: 负责业务逻辑和状态管理
 * - Repository: 负责数据操作和文件管理
 * - Screen: 负责 UI 渲染
 *
 * 注意：此 Activity 直接继承 ComponentActivity 而不是 BaseActivity
 * 因为 Compose 不需要 BaseActivity 提供的 toolbar 功能
 */
class SkinActivity : ComponentActivity() {

    // ViewModel 实例
    private lateinit var viewModel: SkinViewModel

    // 权限请求启动器
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // 权限已授予，可以继续操作
        } else {
            // 权限被拒绝
        }
    }

    // 文件选择器启动器（用于导入皮肤ZIP文件）
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 用户选择了文件，开始导入
            viewModel.importSkinFromZip(it) { success, message ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (success) "导入成功：$message" else "导入失败：$message",
                        Toast.LENGTH_LONG
                    ).show()
                    if (success) {
                        // 刷新皮肤列表
                        viewModel.loadAvailableSkins()
                    }
                }
            }
        }
    }

    // 目录选择器启动器（用于导入皮肤目录）
    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // 用户选择了目录，开始导入
            viewModel.importSkinFromDirectory(it) { success, message ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (success) "导入成功：$message" else "导入失败：$message",
                        Toast.LENGTH_LONG
                    ).show()
                    if (success) {
                        // 刷新皮肤列表
                        viewModel.loadAvailableSkins()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 ViewModel
        viewModel = SkinViewModel(this)

        // 检查并请求权限
        checkAndRequestPermissions()

        // 设置 Compose UI
        setContent {
            // 应用主题
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFE1D9D2), // RGB 225/217/210
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE8EAF6),
                    onPrimaryContainer = Color(0xFF131313),
                    secondary = Color(0xFFD2FFFB), // RGB 210/255/251
                    onSecondary = Color.White,
                    background = Color(0xFFF5F7FA),
                    onBackground = Color.Black,
                    surface = Color.White,
                    onSurface = Color.Black,
                    surfaceVariant = Color(0xFFE0E0E0),
                    onSurfaceVariant = Color(0xFF616161)
                )
            ) {
                // 渲染主屏幕
                SkinScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 刷新状态
        viewModel.refreshState()
    }

    /**
     * 检查并请求存储权限
     *
     * Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限
     * Android 6-10 需要 READ/WRITE_EXTERNAL_STORAGE 权限
     */
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: 需要所有文件访问权限
            if (!android.os.Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10: 需要读写权限
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val needsPermission = permissions.any {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (needsPermission) {
                permissionLauncher.launch(permissions)
            }
        }
    }

    /**
     * 启动皮肤导入流程
     *
     * 打开文件选择器，让用户选择ZIP格式的皮肤包
     */
    fun startImportSkin() {
        filePickerLauncher.launch("application/zip")
    }

    /**
     * 启动皮肤目录导入流程
     *
     * 打开目录选择器，让用户选择皮肤目录
     */
    fun startImportSkinFromDirectory() {
        directoryPickerLauncher.launch(null)
    }
}
