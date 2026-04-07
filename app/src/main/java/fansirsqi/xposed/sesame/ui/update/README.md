# 更新模块使用指南

## 简介

这是一个精简的 Android 应用更新模块，包含检查更新、下载文件、验证文件和安装 APK 的完整功能。

## 文件结构

```
update/
├── UpdateConfig.kt          # 配置类
├── UpdateChecker.kt         # 业务逻辑（检查、下载、验证）
├── UpdateManager.kt         # UI 管理（对话框、进度、安装）
├── UpdateCheckRequest.kt    # 请求数据类
├── UpdateCheckResponse.kt   # 响应数据类
└── ApiResponse.kt          # API 响应包装类
```

## 快速开始

### 1. 基本使用（使用默认配置）

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用默认配置
        updateManager = UpdateManager(
            context = this,
            config = UpdateConfig.DEFAULT,
            coroutineScope = lifecycleScope
        )

        // 检查更新
        updateManager.checkForUpdates()
    }
}
```

### 2. 自定义配置

```kotlin
// 创建自定义配置
val config = UpdateConfig(
    baseUrl = "https://your-server.com",
    appId = "com.your.app",
    channel = "stable",
    downloadDir = "Download",  // 下载目录
    enableFileVerification = true  // 启用文件验证
)

val updateManager = UpdateManager(
    context = this,
    config = config,
    coroutineScope = lifecycleScope
)
```

### 3. 兼容旧版本写法

```kotlin
// 如果不想使用 UpdateConfig，可以直接传参数
val updateManager = UpdateManager(
    context = this,
    baseUrl = "http://10.0.2.2:8085",
    appId = "fansirsqi.xposed.sesame",
    channel = "beta",
    coroutineScope = lifecycleScope
)
```

## 功能说明

### 检查更新

```kotlin
// 自动显示更新对话框
updateManager.checkForUpdates()
```

### 清理旧文件

```kotlin
// 清理下载目录中的旧 APK 文件（保留最新的一个）
val deletedCount = updateManager.cleanOldApkFiles()
Log.d("Update", "删除了 $deletedCount 个旧文件")
```

### 在应用启动时检查更新

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 在应用启动时检查更新
        val updateManager = UpdateManager(
            context = this,
            config = UpdateConfig.DEFAULT,
            coroutineScope = CoroutineScope(Dispatchers.Main)
        )

        updateManager.checkForUpdates()
    }
}
```

## 配置选项

### UpdateConfig 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `baseUrl` | String | 必需 | 服务器地址 |
| `appId` | String | 必需 | 应用 ID |
| `channel` | String | "beta" | 更新渠道（stable/beta/alpha） |
| `downloadDir` | String | "Download" | 下载目录名称 |
| `enableFileVerification` | Boolean | true | 是否启用文件完整性验证 |

### 预定义配置

```kotlin
// 默认配置（用于 Sesame-TK 项目）
UpdateConfig.DEFAULT

// 生产环境配置
UpdateConfig.production(
    baseUrl = "https://api.example.com",
    appId = "com.example.app"
)
```

## 后端 API 要求

### 1. 检查更新接口

**请求：** `POST /api/update/check`

```json
{
  "appId": "fansirsqi.xposed.sesame",
  "currentVersion": "0.2.7",
  "platform": "android",
  "channel": "beta",
  "locale": "zh"
}
```

**响应：**

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {
    "updateAvailable": true,
    "latestVersion": "0.2.8",
    "currentVersion": "0.2.7",
    "forceUpdate": false,
    "releaseNotes": "更新内容...",
    "fileSize": 8459312,
    "files": [
      {
        "fileType": "installer",
        "fileName": "app-v0.2.8.apk",
        "fileKey": "16ebbac25ec643d3a15e20959822e587",
        "md5": "e37b8a1900048c7d4e8b5394a5f1f02d",
        "sha256": "4790b5cb9bc1ef00d09d0e86c1b3b0fd..."
      }
    ]
  }
}
```

### 2. 下载文件接口

**请求：** `GET /api/file/download/{fileKey}`

**响应：** 直接返回文件流（APK 文件）

## 权限要求

在 `AndroidManifest.xml` 中添加：

```xml
<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- 存储权限（Android 10 以下） -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- 安装权限 -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

## FileProvider 配置

在 `AndroidManifest.xml` 中添加：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

创建 `res/xml/file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-path name="external_files" path="." />
</paths>
```

## 注意事项

1. **版本号格式**：自动从 `BuildConfig.VERSION` 提取版本号（如 "v0.2.7-beta-b74p" → "0.2.7"）
2. **文件验证**：默认启用 MD5 和 SHA256 双重验证，确保文件完整性
3. **下载目录**：默认使用 `Download` 目录，可通过配置修改
4. **旧文件清理**：调用 `cleanOldApkFiles()` 可清理旧的 APK 文件，保留最新的一个
5. **线程安全**：所有网络和文件操作在 IO 线程执行，UI 操作自动切换到主线程

## 常见问题

### Q: 如何禁用文件验证？
A: 在配置中设置 `enableFileVerification = false`

### Q: 如何更改下载目录？
A: 在配置中设置 `downloadDir = "YourDirectory"`

### Q: 如何手动清理旧文件？
A: 调用 `updateManager.cleanOldApkFiles()`

### Q: 如何在后台检查更新？
A: 使用 WorkManager 或定时任务调用 `checkForUpdates()`

## 完整示例

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化更新管理器
        updateManager = UpdateManager(
            context = this,
            config = UpdateConfig.DEFAULT,
            coroutineScope = lifecycleScope
        )

        // 检查更新按钮
        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
            updateManager.checkForUpdates()
        }

        // 清理文件按钮
        findViewById<Button>(R.id.btnCleanFiles).setOnClickListener {
            val count = updateManager.cleanOldApkFiles()
            Toast.makeText(this, "删除了 $count 个文件", Toast.LENGTH_SHORT).show()
        }
    }
}
```

## 许可证

MIT License
