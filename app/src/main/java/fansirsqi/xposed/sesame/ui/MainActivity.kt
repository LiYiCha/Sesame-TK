package fansirsqi.xposed.sesame.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.mutableStateOf
import fansirsqi.xposed.sesame.ui.theme.app.SesameTheme
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.data.RunType
import fansirsqi.xposed.sesame.data.UIConfig
import fansirsqi.xposed.sesame.data.ViewAppInfo
import fansirsqi.xposed.sesame.data.ViewAppInfo.verifyId
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.newui.DeviceInfoCard
import fansirsqi.xposed.sesame.newui.DeviceInfoUtil
import fansirsqi.xposed.sesame.ui.extension.ExtensionListActivity
import fansirsqi.xposed.sesame.ui.extra.activity.HelpActivity
import fansirsqi.xposed.sesame.ui.extra.activity.RpcDebugActivity
import fansirsqi.xposed.sesame.ui.logviewer.LogViewerComposeActivity
import fansirsqi.xposed.sesame.ui.network.CaptureSettingsActivity
import fansirsqi.xposed.sesame.util.FansirsqiUtil
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.PermissionUtil
import fansirsqi.xposed.sesame.util.ToastUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import fansirsqi.xposed.sesame.util.AppUpdaterManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb
import fansirsqi.xposed.sesame.ui.theme.app.HolidayTheme


//   欢迎自己打包 欢迎大佬pr
//   项目开源且公益  维护都是自愿
//   但是如果打包改个名拿去卖钱忽悠小白
//   那我只能说你妈死了 就当开源项目给你妈烧纸钱了
class MainActivity : BaseActivity() {
    private var userNameArray = arrayOf("默认")
    private var userEntityArray = arrayOf<UserEntity?>(null)
    private val oneWordText = mutableStateOf("正在获取随机一言...")
    private val deviceInfoState = mutableStateOf<Map<String, String>>(emptyMap())
    private var hasPermissions = false
    private val viewHandler = Handler(Looper.getMainLooper())
    private lateinit var titleRunner: Runnable
    private lateinit var broadcastReceiver: BroadcastReceiver
    
    private val mainActivityThemeObserver: () -> Unit = {
        val isSystemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // 单一数据源：与 BaseActivity / Compose 端共用同一调色板
        val palette =HolidayTheme.resolvePalette(isSystemNight)
        val surfaceColorInt = palette.surface.toArgb()
        val surfaceVariantInt = palette.surfaceVariant.toArgb()
        val textColorInt = palette.onSurface.toArgb()

        val buttons = listOf(R.id.btn_forest_log, R.id.btn_farm_log, R.id.btn_other_log, R.id.btn_extend_function, R.id.btn_github, R.id.btn_settings)
        buttons.forEach { id ->
            val btn = findViewById<MaterialButton>(id)
            btn?.backgroundTintList = ColorStateList.valueOf(surfaceVariantInt)
            btn?.backgroundTintMode = PorterDuff.Mode.SRC_IN
            btn?.setTextColor(ColorStateList.valueOf(textColorInt))
        }
        // 六宫格卡片背景 = surface（与大卡片一致）
        findViewById<MaterialCardView>(R.id.grid_card)?.setCardBackgroundColor(surfaceColorInt)

        HolidayTheme.applyGlobalNightMode()
    }
    


    @SuppressLint("SetTextI18n", "UnsafeDynamicallyLoadedCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        HolidayTheme.applyGlobalNightMode()
        super.onCreate(savedInstanceState)
        ToastUtil.init(this) // 初始化全局 Context

        hasPermissions = PermissionUtil.checkOrRequestFilePermissions(this)
        if (!hasPermissions) {
            Toast.makeText(this, "未获取文件读写权限", Toast.LENGTH_LONG).show()
            finish() // 如果权限未获取，终止当前 Activity
            return
        }

        setContentView(R.layout.activity_main)
       HolidayTheme.themeObservers.add(mainActivityThemeObserver)
        mainActivityThemeObserver.invoke()
        val deviceInfo: ComposeView = findViewById(R.id.device_info)

        deviceInfoState.value = DeviceInfoUtil.showInfo(verifyId, "未登录")
        deviceInfo.setContent {
            SesameTheme {
                DeviceInfoCard(
                    info = deviceInfoState.value,
                    oneWord = oneWordText.value
                )
            }
        }

        // 初始化时设置状态为 DISABLE（已禁用）
        ViewAppInfo.veriftag = false
        updateSubTitle(RunType.DISABLE.nickName)

        // 设置标题更新的 Runnable
        titleRunner = Runnable { updateSubTitle(RunType.DISABLE.nickName) }

        // 注册广播接收器
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                Log.runtime("receive broadcast:$action intent:$intent")
                when (action) {
                    General.ACTION_STATUS_REPLY -> {
                        if (!ViewAppInfo.veriftag) {
                            ViewAppInfo.veriftag = true
                            updateSubTitle(RunType.LOADED.nickName)
                        }
                        runCatching {
                            val fw = intent.getStringExtra("framework")
                            if (!fw.isNullOrBlank()) {
                                ViewAppInfo.xpFrameworkVersion = fw
                                val currentUser = deviceInfoState.value["当前账号"] ?: "未登录"
                                deviceInfoState.value = DeviceInfoUtil.showInfo(verifyId, currentUser)
                            }
                        }
                        viewHandler.removeCallbacks(titleRunner)
                    }
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(General.ACTION_STATUS_REPLY)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }

        //随机一言
        lifecycleScope.launch {
            val result = FansirsqiUtil.getOneWord()
            oneWordText.value = result
        }
        

        // 启动时在后台静默对账清理残留安装包，并依设置检测更新
        AppUpdaterManager.initAndCheckOnStartup(this)
    }

    override fun onResume() {
        super.onResume()
        val runTypeStr = if (ViewAppInfo.veriftag) RunType.LOADED.nickName else RunType.DISABLE.nickName
        updateSubTitle(runTypeStr)
        
        if (hasPermissions) {
            // 如果当前状态是禁用，3秒后更新为禁用状态
            if (!ViewAppInfo.veriftag) {
                viewHandler.postDelayed(titleRunner, 3000)
                try {
                    sendBroadcast(Intent(General.ACTION_STATUS_QUERY))
                } catch (th: Throwable) {
                    Log.runtime("view sendBroadcast status err:")
                    Log.printStackTrace(th)
                }
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    //打开设置前需要确认设置了哪个UI
                    UIConfig.load()
                } catch (e: Exception) {
                    Log.printStackTrace(e)
                }

                try {
                    val activeUserId = Files.getActiveUser()
                    var activeUserShow = "未登录"
                    if (!activeUserId.isNullOrEmpty()) {
                        UserMap.loadSelf(activeUserId)
                        val activeUserEntity = UserMap.get(activeUserId)
                        activeUserShow = if (activeUserEntity == null) {
                            activeUserId
                        } else {
                            activeUserEntity.showName + " (" + activeUserEntity.account + ")"
                        }
                    }

                    val userNameList: MutableList<String> = ArrayList()
                    val userEntityList: MutableList<UserEntity?> = ArrayList()
                    val configFiles = Files.CONFIG_DIR.listFiles()
                    if (configFiles != null) {
                        for (configDir in configFiles) {
                            if (Files.isUserDirectory(configDir)) {
                                val userId = configDir.name
                                UserMap.loadSelf(userId)
                                val userEntity = UserMap.get(userId)
                                var userName = if (userEntity == null) {
                                    userId
                                } else {
                                    userEntity.showName + ": " + userEntity.account
                                }
                                if (!activeUserId.isNullOrEmpty() && userId == activeUserId) {
                                    userName += " (当前)"
                                }
                                userNameList.add(userName)
                                userEntityList.add(userEntity)
                            }
                        }
                    }
                    var defaultName = "默认"
                    if (activeUserId.isNullOrEmpty() || activeUserId == "默认" || activeUserId == "default") {
                        defaultName += " (当前)"
                    }
                    userNameList.add(0, defaultName)
                    userEntityList.add(0, null)

                    val newInfo = DeviceInfoUtil.showInfo(verifyId, activeUserShow)

                    withContext(Dispatchers.Main) {
                        userNameArray = userNameList.toTypedArray<String>()
                        userEntityArray = userEntityList.toTypedArray<UserEntity?>()
                        deviceInfoState.value = newInfo
                    }
                } catch (e: Exception) {
                    Log.printStackTrace(e)
                    withContext(Dispatchers.Main) {
                        userNameArray = arrayOf("默认")
                        userEntityArray = arrayOf(null)
                        deviceInfoState.value = DeviceInfoUtil.showInfo(verifyId, "未登录")
                    }
                }
            }

        }
    }

    fun onClick(v: View) {
        val path = when (v.id) {
            R.id.btn_forest_log -> Files.getForestLogFile().absolutePath
            R.id.btn_farm_log -> Files.getFarmLogFile().absolutePath
            R.id.btn_other_log -> Files.getOtherLogFile().absolutePath
            R.id.btn_github -> Files.getDebugLogFile().absolutePath
            R.id.btn_settings -> {
                showSelectionDialog(
                    "📌 请选择配置", userNameArray, { index: Int -> this.goSettingActivity(index) }, "不选", {}
                )
                return
            }
            R.id.btn_extend_function -> {
                startActivity(Intent(this, ExtensionListActivity::class.java))
                return
            }
            else -> return
        }
        openLogViewer(path)
    }

    private fun openLogViewer(path: String) {
        startActivity(Intent(this, LogViewerComposeActivity::class.java).apply {
            putExtra("canClear", true)
            data = "file://$path".toUri()
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        try {
            menu.add(0, 1, 1, R.string.view_error_log_file)    // 异常日志
            menu.add(0, 2, 2, R.string.view_runtim_log_file)  // 运行日志
            menu.add(0, 3, 3, R.string.view_capture)             // 抓包记录 (rpc)
            menu.add(0, 4, 4, R.string.extend)         // 扩展功能
            menu.add(0, 5, 5, R.string.settings)               // 设置
            menu.add(0, 6, 6, R.string.test_post)              // 模拟请求(RpcDebugActivity)
            menu.add(0, 7, 7, R.string.capture_settings)       // 抓包设置
            if (BuildConfig.DEBUG) {
                menu.add(0, 8, 8, R.string.clearn)        // 清空配置
            }
            menu.add(0, 9, 9, R.string.help)  // 帮助页面
        } catch (e: Exception) {
            Log.printStackTrace(e)
            ToastUtil.makeText(this, "菜单创建失败，请重试", Toast.LENGTH_SHORT).show()
            return false
        }
        return super.onCreateOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            //异常日志
            1 -> openLogViewer(Files.getErrorLogFile().absolutePath)
            //运行日志
            2 -> openLogViewer(Files.getRuntimeLogFile().absolutePath)
            // 抓包日志
            3 -> openLogViewer(Files.getCaptureLogFile().absolutePath)
            // 扩展功能
            4 -> startActivity(Intent(this, ExtendActivity::class.java))
            //设置
            5 -> selectSettingUid()
            //模拟请求
            6 -> startActivity(Intent(this, RpcDebugActivity::class.java))
            // 抓包设置
            7 -> startActivity(Intent(this, CaptureSettingsActivity::class.java))
            //清空配置
            8 -> AlertDialog.Builder(this).setTitle("⚠️ 警告").setMessage("🤔 确认清除所有模块配置？").setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                if (Files.delFile(Files.CONFIG_DIR)) {
                    Toast.makeText(this, "🙂 清空配置成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "😭 清空配置失败", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }.create().show()
            //帮助页面
            9 -> startActivity(Intent(this, HelpActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectSettingUid() {
        StringDialog.showSelectionDialog(this, "📌 请选择配置", userNameArray, { dialog1: DialogInterface, which: Int ->
            goSettingActivity(which)
            dialog1.dismiss()
        }, "返回", { dialog1: DialogInterface ->
            dialog1.dismiss()
        })
    }
    /**
     * 显示选择对话框
     *
     * @param title                  标题
     * @param options                选项
     * @param onItemSelected         选项选中的回调
     * @param negativeButtonText     取消按钮的文本
     * @param onNegativeButtonClick  取消按钮的点击事件
     */
    private fun showSelectionDialog(
        title: String?, options: Array<String>, onItemSelected: Consumer<Int>, negativeButtonText: String?, onNegativeButtonClick: Runnable
    ) {
        StringDialog.showSelectionDialog(this, title, options, { dialog1: DialogInterface, which: Int ->
            onItemSelected.accept(which)
            dialog1.dismiss()
        }, negativeButtonText, { dialog1: DialogInterface ->
            onNegativeButtonClick.run()
            dialog1.dismiss()
        })
    }

    private fun goSettingActivity(index: Int) {
            val userEntity = userEntityArray[index]
            val targetActivity = UIConfig.INSTANCE.targetActivityClass
            val intent = Intent(this, targetActivity)
            if (userEntity != null) {
                intent.putExtra("userId", userEntity.userId)
                intent.putExtra("userName", userEntity.showName)
            } else {
                intent.putExtra("userName", userNameArray[index])
            }

            startActivity(intent)
    }

    fun updateSubTitle(runType: String) {
        baseTitle = ViewAppInfo.appTitle + "[" + runType + "]"
        updateToolbarTheme()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::titleRunner.isInitialized) {
            viewHandler.removeCallbacks(titleRunner)
        }
        if (::broadcastReceiver.isInitialized) {
            try {
                unregisterReceiver(broadcastReceiver)
            } catch (e: Exception) {
                Log.printStackTrace(e)
            }
        }
        HolidayTheme.themeObservers.remove(mainActivityThemeObserver)
    }
}
