package fansirsqi.xposed.sesame.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.ui.logviewer.LogViewerComposeActivity
import fansirsqi.xposed.sesame.ui.update.UpdateManager
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.data.RunType
import fansirsqi.xposed.sesame.data.UIConfig
import fansirsqi.xposed.sesame.data.ViewAppInfo
import fansirsqi.xposed.sesame.data.ViewAppInfo.verifyId
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.newui.DeviceInfoCard
import fansirsqi.xposed.sesame.newui.DeviceInfoUtil
import fansirsqi.xposed.sesame.newui.WatermarkView
import fansirsqi.xposed.sesame.ui.extra.activity.HelpActivity
import fansirsqi.xposed.sesame.ui.extra.activity.RpcDebugActivity
import fansirsqi.xposed.sesame.ui.network.NetworkListActivity
import fansirsqi.xposed.sesame.ui.network.NetworkPacketListActivity
import fansirsqi.xposed.sesame.ui.update.UpdateConfig
import fansirsqi.xposed.sesame.util.FansirsqiUtil
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch


//   欢迎自己打包 欢迎大佬pr
//   项目开源且公益  维护都是自愿
//   但是如果打包改个名拿去卖钱忽悠小白
//   那我只能说你妈死了 就当开源项目给你妈烧纸钱了
class MainActivity : BaseActivity() {
    private val TAG = "MainActivity"
    private var userNameArray = arrayOf("默认")
    private var userEntityArray = arrayOf<UserEntity?>(null)
    private lateinit var oneWord: TextView
    private var hasPermissions = false
    private var isClick = false
    private val viewHandler = Handler(Looper.getMainLooper())
    private lateinit var titleRunner: Runnable
    private var userNickName: String = ""
    
    // 更新管理器
    private lateinit var updateManager: UpdateManager

    @SuppressLint("SetTextI18n", "UnsafeDynamicallyLoadedCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ToastUtil.init(this) // 初始化全局 Context

        hasPermissions = fansirsqi.xposed.sesame.util.PermissionUtil.checkOrRequestFilePermissions(this)
        if (!hasPermissions) {
            Toast.makeText(this, "未获取文件读写权限", Toast.LENGTH_LONG).show()
            finish() // 如果权限未获取，终止当前 Activity
            return
        }

        setContentView(R.layout.activity_main)
        oneWord = findViewById(R.id.one_word)
        val deviceInfo: ComposeView = findViewById(R.id.device_info)
        val v = WatermarkView.install(this)
        deviceInfo.setContent {
            val customColorScheme = lightColorScheme(
                primary = Color(0xFF3F51B5), onPrimary = Color.White, background = Color(0xFFF5F5F5), onBackground = Color.Black
            )
            MaterialTheme(colorScheme = customColorScheme) {
                DeviceInfoCard(DeviceInfoUtil.showInfo(verifyId))
            }
        }

        // 初始化时设置状态为 DISABLE（已禁用）
        ViewAppInfo.veriftag = false
        updateSubTitle(RunType.DISABLE.nickName)

        // 设置标题更新的 Runnable
        titleRunner = Runnable { updateSubTitle(RunType.DISABLE.nickName) }

        // 注册广播接收器
        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                Log.runtime("receive broadcast:$action intent:$intent")
                when (action) {
                    "fansirsqi.xposed.sesame.status" -> {
                        if (!ViewAppInfo.veriftag) {
                            ViewAppInfo.veriftag = true
                            updateSubTitle(RunType.LOADED.nickName)
                        }
                        viewHandler.removeCallbacks(titleRunner)
                        if (isClick) {
                            Toast.makeText(context, "芝麻粒状态加载正常👌", Toast.LENGTH_SHORT).show()
                            isClick = false
                        }
                    }
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction("fansirsqi.xposed.sesame.status")
            addAction("fansirsqi.xposed.sesame.update")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }

        //随机一言
        lifecycleScope.launch {
            val result = FansirsqiUtil.getOneWord()
            oneWord.text = result
        }
        
//        // 初始化更新管理器
//        updateManager = UpdateManager(
//            this,
//            UpdateConfig.DEFAULT,
//            lifecycleScope)
//
//        // 检查更新（异步非阻塞，延迟3秒执行，不影响启动体验）
//        // 内部使用协程，下载在后台线程进行，不会阻塞主线程
//        viewHandler.postDelayed({
//            updateManager.checkForUpdates()
//        }, 3000)

//        // 验证通过
//        lifecycleScope.launch {
//             ViewAppInfo.veriftag = true
//            updateSubTitle(RunType.LOADED.nickName)
//        }

    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions) {
            // 如果当前状态是禁用，3秒后更新为禁用状态
            if (!ViewAppInfo.veriftag) {
                viewHandler.postDelayed(titleRunner, 3000)
                try {
                    sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.status"))
                } catch (th: Throwable) {
                    Log.runtime("view sendBroadcast status err:")
                    Log.printStackTrace(th)
                }
            }

            try {
                //打开设置前需要确认设置了哪个UI
                UIConfig.load()
            } catch (e: Exception) {
                Log.printStackTrace(e)
            }

            try {
                val userNameList: MutableList<String> = ArrayList()
                val userEntityList: MutableList<UserEntity?> = ArrayList()
                val configFiles = Files.CONFIG_DIR.listFiles()
                if (configFiles != null) {
                    for (configDir in configFiles) {
                        if (configDir.isDirectory) {
                            val userId = configDir.name
                            UserMap.loadSelf(userId)
                            val userEntity = UserMap.get(userId)
                            val userName = if (userEntity == null) {
                                userId
                            } else {
                                userEntity.showName + ": " + userEntity.account
                            }
                            userNameList.add(userName)
                            userEntityList.add(userEntity)
                        }
                    }
                }
                userNameList.add(0, "默认")
                userEntityList.add(0, null)
                userNameArray = userNameList.toTypedArray<String>()
                userEntityArray = userEntityList.toTypedArray<UserEntity?>()
            } catch (e: Exception) {
                userNameArray = arrayOf("默认")
                userEntityArray = arrayOf(null)
                Log.printStackTrace(e)
            }

        }
    }
    /**
     * 检查支付宝运行状态
     */
    private fun checkAlipayRunningStatus() {
        try {
            // 检查支付宝服务是否在运行
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = am.getRunningServices(Int.MAX_VALUE)
            var isAlipayRunning = false

            for (service in runningServices) {
                if (service.service.className.contains("com.eg.android.AlipayGphone")) {
                    isAlipayRunning = true
                    break
                }
            }

            // 如果支付宝没有运行且当前状态不是禁用，则设置为禁用
            if (!isAlipayRunning && ViewAppInfo.veriftag) {
                ViewAppInfo.veriftag = false
                updateSubTitle(RunType.DISABLE.nickName)
            }
        } catch (e: Exception) {
            // 忽略权限等问题导致的异常
        }
    }

    fun onClick(v: View) {
        var data = "file://"
        val id = v.id
        when (id) {
            R.id.btn_forest_log -> {
                data += Files.getForestLogFile().absolutePath
            }

            R.id.btn_farm_log -> {
                data += Files.getFarmLogFile().absolutePath
            }

            R.id.btn_other_log -> {
                data += Files.getOtherLogFile().absolutePath
            }

            R.id.btn_github -> {
                data += Files.getDebugLogFile().absolutePath
            }

            R.id.btn_settings -> {
                showSelectionDialog(
                    "📌 请选择配置", userNameArray, { index: Int -> this.goSettingActivity(index) }, "😡 老子就不选", {}, true
                )
                return
            }

            R.id.btn_friend_watch -> {
                startActivity(Intent(this, fansirsqi.xposed.sesame.ui.extension.ExtensionListActivity::class.java))
                return
            }

            R.id.one_word -> {
                oneWord.text = "正在获取句子，请稍后……"
                //updateSubTitle(RunType.LOADED.nickName)

                lifecycleScope.launch {
                    val result = FansirsqiUtil.getOneWord()
                    oneWord.text = result
                }
                return
            }
        }
        val it = Intent(this, LogViewerComposeActivity::class.java)
        it.putExtra("canClear", true);
        it.data = data.toUri()
        startActivity(it)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        try {
            // 使用清单文件中定义的完整别名
            val aliasComponent = ComponentName(this, General.MODULE_PACKAGE_UI_ICON)
            val state = packageManager.getComponentEnabledSetting(aliasComponent)
            // 注意状态判断逻辑修正
//        val isEnabled = state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
//        menu.add(0, 1, 1, R.string.hide_the_application_icon).setCheckable(true).isChecked = !isEnabled
            menu.add(0, 2, 2, R.string.view_error_log_file)    // 异常日志
            menu.add(0, 3, 3, R.string.view_all_log_file)      // 全部日志
            menu.add(0, 4, 4, R.string.view_runtim_log_file)   // 运行日志
            menu.add(0, 5, 5, R.string.view_capture)           // 抓包记录 (旧版)
            menu.add(0, 6, 6, R.string.extend)                 // 扩展功能
            menu.add(0, 7, 7, R.string.settings)               // 设置
            menu.add(0, 8, 8, R.string.test_post)              // 模拟请求(RpcDebugActivity)
            menu.add(0, 9, 9, "流量抓包查看")                   // 新版抓包浏览器
            if (BuildConfig.DEBUG) {
                menu.add(0, 10, 10, R.string.clearn)           // 清空配置
            }
            menu.add(0, 11, 11, R.string.help)  // 帮助页面
        } catch (e: Exception) {
            Log.printStackTrace(e)
            ToastUtil.makeText(this, "菜单创建失败，请重试", Toast.LENGTH_SHORT).show()
            return false
        }
        return super.onCreateOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {

                return true
            }

            2 -> {
                var errorData = "file://"
                errorData += Files.getErrorLogFile().absolutePath
                val errorIt = Intent(this, LogViewerComposeActivity::class.java)
                errorIt.putExtra("nextLine", false)
                errorIt.putExtra("canClear", true)
                errorIt.data = errorData.toUri()
                startActivity(errorIt)
            }

            3 -> {
                var recordData = "file://"
                recordData += Files.getRecordLogFile().absolutePath
                val otherIt = Intent(this, LogViewerComposeActivity::class.java)
                otherIt.putExtra("nextLine", false)
                otherIt.putExtra("canClear", true)
                otherIt.data = recordData.toUri()
                startActivity(otherIt)
            }
            //运行日志
            4 -> {
                var runtimeData = "file://"
                runtimeData += Files.getRuntimeLogFile().absolutePath
                val allIt = Intent(this, LogViewerComposeActivity::class.java)
                allIt.putExtra("nextLine", false)
                allIt.putExtra("canClear", true)
                allIt.data = runtimeData.toUri()
                startActivity(allIt)
            }
            // 抓包日志
            5 -> {
                var captureData = "file://"
                captureData += Files.getCaptureLogFile().absolutePath
                val captureIt = Intent(this, LogViewerComposeActivity::class.java)
                captureIt.putExtra("nextLine", false)
                captureIt.putExtra("canClear", true)
                captureIt.data = captureData.toUri()
                startActivity(captureIt)
            }

            6 -> // 扩展功能
                startActivity(Intent(this, ExtendActivity::class.java))
            //设置
            7 -> selectSettingUid()
            //模拟请求
            8 -> {
                val rpcDebugIntent = Intent(this, RpcDebugActivity::class.java)
                startActivity(rpcDebugIntent)
            }
            // 流量抓包查看
            9 -> {
                startActivity(Intent(this, NetworkPacketListActivity::class.java))
            }
            //清空配置
            10 -> AlertDialog.Builder(this).setTitle("⚠️ 警告").setMessage("🤔 确认清除所有模块配置？").setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                if (Files.delFile(Files.CONFIG_DIR)) {
                    Toast.makeText(this, "🙂 清空配置成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "😭 清空配置失败", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }.create().show()
            //帮助页面
            11 -> startActivity(Intent(this, HelpActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectSettingUid() {
        val latch = CountDownLatch(1)
        val dialog = StringDialog.showSelectionDialog(this, "📌 请选择配置", userNameArray, { dialog1: DialogInterface, which: Int ->
            goSettingActivity(which)
            dialog1.dismiss()
            latch.countDown()
        }, "返回", { dialog1: DialogInterface ->
            dialog1.dismiss()
            latch.countDown()
        })

//        val length = userNameArray.size
//        if (length in 1..2) {
//            // 定义超时时间（单位：毫秒）
//            val timeoutMillis: Long = 800
//            Thread {
//                try {
//                    if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
//                        runOnUiThread {
//                            if (dialog.isShowing) {
//                                goSettingActivity(length - 1)
//                                dialog.dismiss()
//                            }
//                        }
//                    }
//                } catch (_: InterruptedException) {
//                    Thread.currentThread().interrupt()
//                }
//            }.start()
//        }
    }
    /**
     * 显示选择对话框
     *
     * @param title                  标题
     * @param options                选项
     * @param onItemSelected         选项选中的回调
     * @param negativeButtonText     取消按钮的文本
     * @param onNegativeButtonClick  取消按钮的点击事件
     * @param showDefaultOption      是否显示默认选项
     */
    private fun showSelectionDialog(
        title: String?, options: Array<String>, onItemSelected: Consumer<Int>, negativeButtonText: String?, onNegativeButtonClick: Runnable, showDefaultOption: Boolean
    ) {
        StringDialog.showSelectionDialog(this, title, options, { dialog1: DialogInterface, which: Int ->
            onItemSelected.accept(which)
            dialog1.dismiss()
        }, negativeButtonText, { dialog1: DialogInterface ->
            onNegativeButtonClick.run()
            dialog1.dismiss()
        })
    }

//    private fun showSelectionDialog(
//        title: String?, options: Array<String>, onItemSelected: Consumer<Int>, negativeButtonText: String?, onNegativeButtonClick: Runnable, showDefaultOption: Boolean
//    ) {
//        val latch = CountDownLatch(1)
//        val dialog = StringDialog.showSelectionDialog(this, title, options, { dialog1: DialogInterface, which: Int ->
//            onItemSelected.accept(which)
//            dialog1.dismiss()
//            latch.countDown()
//        }, negativeButtonText, { dialog1: DialogInterface ->
//            onNegativeButtonClick.run()
//            dialog1.dismiss()
//            latch.countDown()
//        })
//
//        val length = options.size
//        if (showDefaultOption && length > 0 && length < 3) {
//            // 定义超时时间（单位：毫秒）
//            val timeoutMillis: Long = 800
//            Thread {
//                try {
//                    if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
//                        runOnUiThread {
//                            if (dialog.isShowing) {
//                                onItemSelected.accept(length - 1)
//                                dialog.dismiss()
//                            }
//                        }
//                    }
//                } catch (_: InterruptedException) {
//                    Thread.currentThread().interrupt()
//                }
//            }.start()
//        }
//    }

    private fun goSettingActivity(index: Int) {
//        if (Detector.loadLibrary("checker")) {
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
//        } else {
//            Detector.tips(this, "缺少必要依赖！")
//        }
    }

    fun updateSubTitle(runType: String) {
        baseTitle = ViewAppInfo.appTitle + "[" + runType + "]" + userNickName
        //Log.runtime("updateSubTitle: $baseTitle")
        when (runType) {
            RunType.DISABLE.nickName -> setBaseTitleTextColor(
                ContextCompat.getColor(
                    this, R.color.not_active_text
                )
            )

            RunType.ACTIVE.nickName -> setBaseTitleTextColor(
                ContextCompat.getColor(
                    this, R.color.active_text
                )
            )

            RunType.LOADED.nickName -> setBaseTitleTextColor(
                ContextCompat.getColor(
                    this, R.color.textColorPrimary
                )
            )
        }
    }
}
