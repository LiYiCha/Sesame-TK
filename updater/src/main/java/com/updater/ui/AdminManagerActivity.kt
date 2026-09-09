package com.updater.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.updater.admin.AdminRepository
import com.updater.config.UpdaterConfigManager
import com.updater.model.UpdateSourceType
import org.json.JSONObject

/**
 * 安装包与发布管理页面（壳层）
 *
 * 分层职责（最佳实践拆分）：
 * - admin/AdminRepository.kt：全部网络与文件业务（登录/清单/删除/分片上传）
 * - ui/AdminManagerScreen.kt：无状态 Compose 界面组件
 * - 本文件：持有 UI 状态、连接 Screen 回调与 Repository，不含任何布局代码
 */
class AdminManagerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_ID = "app_id"
        private const val REQUEST_CODE_PICK_APK = 8902
    }

    private lateinit var configManager: UpdaterConfigManager
    private val repo = AdminRepository()

    private var targetAppId: String = ""
    private var baseHost by mutableStateOf("")
    private var matchedAppId: String = ""
    private var appJson: JSONObject = JSONObject()

    // UI 状态
    private var loggedIn by mutableStateOf(false)
    private var adminUsername by mutableStateOf("")
    private var packages by mutableStateOf<List<JSONObject>>(emptyList())
    private var selectedFile by mutableStateOf<AdminRepository.PickedApk?>(null)
    private var pkgName by mutableStateOf("")
    private var pkgDesc by mutableStateOf("")
    private var loginInFlight by mutableStateOf(false)
    private var busyMessage by mutableStateOf<String?>(null)
    private var uploadProgress by mutableStateOf<UploadProgressInfo?>(null)
    private var dialogInfo by mutableStateOf<AdminDialogInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configManager = UpdaterConfigManager(this)
        targetAppId = intent.getStringExtra(EXTRA_APP_ID) ?: packageName
        if (targetAppId.isBlank()) targetAppId = packageName

        setupBaseHost()
        loggedIn = configManager.isAdminLoggedIn
        adminUsername = configManager.adminUsername

        enableEdgeToEdge()
        setContent {
            UpdaterComposeTheme {
                AdminManagerScreen(
                    baseHost = baseHost,
                    targetAppId = targetAppId,
                    adminUsername = adminUsername,
                    loggedIn = loggedIn,
                    packages = packages,
                    selectedFile = selectedFile,
                    pkgName = pkgName,
                    pkgDesc = pkgDesc,
                    loginInFlight = loginInFlight,
                    busyMessage = busyMessage,
                    uploadProgress = uploadProgress,
                    dialogInfo = dialogInfo,
                    onBack = { finish() },
                    onRefresh = { refresh() },
                    onLogin = { user, pass -> doLogin(user, pass) },
                    onLogout = {
                        configManager.logoutAdmin()
                        loggedIn = false
                        adminUsername = ""
                        packages = emptyList()
                    },
                    onPickFile = { requestPickApk() },
                    onPkgNameChange = { pkgName = it },
                    onPkgDescChange = { pkgDesc = it },
                    onStartUpload = { startUploadProcess() },
                    onDeletePackage = { pkg, index -> executeDeletePackage(pkg, index) },
                    onDismissDialog = { dialogInfo = null }
                )
            }
        }

        if (loggedIn && baseHost.isNotBlank()) {
            loadPackages()
        }
    }

    private fun setupBaseHost() {
        val cfSource = configManager.getSources().find { it.type == UpdateSourceType.CLOUDFLARE_R2 }
            ?: configManager.getSelectedSource()
        baseHost = (cfSource?.url ?: "").trimEnd('/')
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun refresh() {
        if (configManager.isAdminLoggedIn) {
            loggedIn = true
            adminUsername = configManager.adminUsername
            loadPackages()
        }
    }

    // ---------- 清单加载 ----------

    private fun loadPackages() {
        repo.loadSnapshot(baseHost, targetAppId, configManager.adminToken) { result ->
            result.onSuccess { snap ->
                matchedAppId = snap.matchedAppId
                appJson = snap.appJson
                packages = snap.packages
            }.onFailure { toast(it.message ?: "获取安装包清单失败") }
        }
    }

    // ---------- 登录 / 登出 ----------

    private fun doLogin(user: String, pass: String) {
        if (user.isEmpty() || pass.isEmpty()) {
            toast("请输入账号和密码")
            return
        }
        loginInFlight = true
        repo.login(baseHost, user, pass) { result ->
            loginInFlight = false
            result.onSuccess { token ->
                configManager.adminToken = token
                configManager.adminUsername = user
                loggedIn = true
                adminUsername = user
                toast("登录成功")
                loadPackages()
            }.onFailure { toast(it.message ?: "登录失败") }
        }
    }

    // ---------- 文件选取 ----------

    private fun requestPickApk() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择 APK 文件"), REQUEST_CODE_PICK_APK)
        } catch (_: Exception) {
            try {
                val docIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(docIntent, REQUEST_CODE_PICK_APK)
            } catch (e2: Exception) {
                toast("调起文件选择器失败: ${e2.message}")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_APK && resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: data.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return
            repo.pickApk(this, uri) { result ->
                result.onSuccess { picked ->
                    selectedFile = picked
                    if (pkgName.isBlank()) {
                        pkgName = picked.appLabel.ifBlank { picked.file.nameWithoutExtension }
                    }
                }.onFailure { toast(it.message ?: "解析本地文件失败") }
            }
        }
    }

    // ---------- 删除 ----------

    private fun executeDeletePackage(pkg: JSONObject, index: Int) {
        busyMessage = "正在从网盘物理删除文件并更新发布清单..."
        repo.deletePackage(
            baseHost = baseHost,
            token = configManager.adminToken,
            targetAppId = targetAppId,
            snapshot = AdminRepository.Snapshot(matchedAppId, appJson, packages),
            pkg = pkg,
            index = index
        ) { result ->
            busyMessage = null
            result.onSuccess { snap ->
                matchedAppId = snap.matchedAppId
                appJson = snap.appJson
                packages = snap.packages
                toast("删除成功！")
                setResult(Activity.RESULT_OK)
                loadPackages() // 静默后台重新拉取对齐
            }.onFailure { err ->
                dialogInfo = AdminDialogInfo("删除失败", err.message ?: "未知错误")
                loadPackages()
            }
        }
    }

    // ---------- 分片上传 + 挂钩发布 ----------

    private fun startUploadProcess() {
        val file = selectedFile
        if (file == null || !file.file.exists() || file.sizeBytes == 0L) {
            toast("请先选择有效的 APK 文件")
            return
        }
        if (configManager.adminToken.isBlank()) {
            toast("管理员登录已过期，请重新登录")
            return
        }

        val title = pkgName.trim().ifEmpty { file.file.nameWithoutExtension }
        uploadProgress = UploadProgressInfo(0, "准备分片直传...")

        repo.uploadAndPublish(
            baseHost = baseHost,
            token = configManager.adminToken,
            targetAppId = targetAppId,
            snapshot = AdminRepository.Snapshot(matchedAppId, appJson, packages),
            picked = file,
            pkgTitle = title,
            pkgDesc = pkgDesc.trim(),
            onProgress = { percent, message ->
                uploadProgress = UploadProgressInfo(percent, message)
            }
        ) { result ->
            uploadProgress = null
            result.onSuccess { snap ->
                matchedAppId = snap.matchedAppId
                appJson = snap.appJson
                packages = snap.packages
                toast("上传并挂钩发布成功！")
                setResult(Activity.RESULT_OK)
                // 重置上传框
                selectedFile = null
                pkgName = ""
                pkgDesc = ""
                loadPackages()
            }.onFailure { err ->
                dialogInfo = AdminDialogInfo("挂钩发布失败", err.message ?: "未知错误")
            }
        }
    }
}
