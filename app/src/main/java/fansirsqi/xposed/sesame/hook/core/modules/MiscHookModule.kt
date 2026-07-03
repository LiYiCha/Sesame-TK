package fansirsqi.xposed.sesame.hook.core.modules

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.hook.core.HookModule
import fansirsqi.xposed.sesame.util.Log
import java.util.Locale

class MiscHookModule : HookModule {
    private val TAG = "MiscHookModule"

    override fun onHandleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (General.PACKAGE_NAME != lpparam.packageName) return
        val classLoader = lpparam.classLoader

        // hook FgBgMonitorImpl (在 main dex 中，可立即 hook，增加 UI 调用栈保护)
        val fgBgClass = "com.alipay.mobile.common.fgbg.FgBgMonitorImpl"
        try {
            val fgBgHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isCalledFromUI()) return
                    param.result = false
                }
            }
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackground", fgBgHook)
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackground", Boolean::class.javaPrimitiveType, fgBgHook)
            XposedHelpers.findAndHookMethod(fgBgClass, classLoader, "isInBackgroundV2", fgBgHook)
            Log.runtime(TAG, "hook FgBgMonitorImpl successfully with UI safety check")
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook FgBgMonitorImpl err: ${t.message}")
        }

        // hook MiscUtils (在 main dex 中，可立即 hook，增加 UI 调用栈保护)
        try {
            XposedHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", classLoader, "isAtFrontDesk",
                Context::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isCalledFromUI()) return
                        param.result = true
                    }
                })
            Log.runtime(TAG, "hook MiscUtils successfully with UI safety check")
        } catch (t: Throwable) {
            Log.runtime(TAG, "hook MiscUtils err")
        }

        // hook findServiceByInterface (在 main dex 中，可立即 hook)
        try {
            val contextImplClass = classLoader.loadClass("com.alipay.mobile.core.impl.MicroApplicationContextImpl")
            XposedBridge.hookAllMethods(contextImplClass, "findServiceByInterface", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result == null && param.args != null && param.args.isNotEmpty()) {
                        val arg = param.args[0]
                        val interfaceName = when (arg) {
                            is String -> arg
                            is Class<*> -> arg.name
                            else -> null
                        }
                        if (interfaceName == "com.alipay.android.phone.businesscommon.advertisement.AdvertisementService") {
                            Log.runtime(TAG, "🔍 AdvertisementService 尚未就绪，返回动态代理以防 NPE 闪退")
                            try {
                                val hostClassLoader = param.thisObject.javaClass.classLoader
                                val serviceClass = hostClassLoader.loadClass(interfaceName)
                                val proxy = java.lang.reflect.Proxy.newProxyInstance(
                                    hostClassLoader,
                                    arrayOf(serviceClass),
                                    object : java.lang.reflect.InvocationHandler {
                                        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any>?): Any? {
                                            val returnType = method.returnType
                                            if (returnType.isPrimitive) {
                                                return when (returnType) {
                                                    java.lang.Boolean.TYPE -> false
                                                    java.lang.Void.TYPE -> null
                                                    java.lang.Long.TYPE -> 0L
                                                    java.lang.Double.TYPE -> 0.0
                                                    java.lang.Float.TYPE -> 0.0f
                                                    java.lang.Integer.TYPE -> 0
                                                    java.lang.Byte.TYPE -> 0.toByte()
                                                    java.lang.Short.TYPE -> 0.toShort()
                                                    java.lang.Character.TYPE -> '\u0000'
                                                    else -> 0
                                                }
                                            }
                                            return null
                                        }
                                    }
                                )
                                param.result = proxy
                            } catch (ex: Throwable) {
                                Log.runtime(TAG, "创建 AdvertisementService 代理失败: ${ex.message}")
                            }
                        }
                    }
                }
            })
            Log.runtime(TAG, "✅ Hook findServiceByInterface 成功")
        } catch (t: Throwable) {
            Log.runtime(TAG, "❌ Hook findServiceByInterface 失败: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "MiscHookModule"
        private val bundleHooksRegistered = java.util.concurrent.atomic.AtomicBoolean(false)
        private val failedSoSet: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

        private val load0Method: java.lang.reflect.Method? by lazy {
            try {
                java.lang.Runtime::class.java.getDeclaredMethod("load0", ClassLoader::class.java, String::class.java).apply {
                    isAccessible = true
                }
            } catch (t: Throwable) {
                Log.runtime(TAG, "获取 Runtime.load0 方法失败: ${t.message}")
                null
            }
        }

        @JvmStatic
        fun delayRegisterBundleHooks(classLoader: ClassLoader) {
            if (!bundleHooksRegistered.compareAndSet(false, true)) {
                return
            }
            Log.runtime(TAG, "开始执行延迟的动态 bundle 及 Activity Hook 注册...")

            // 1. Hook H5AppRpcUpdate
            try {
                val targetClass = classLoader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate")
                val h5PageClass = classLoader.loadClass(General.H5PAGE_NAME)
                XposedHelpers.findAndHookMethod(
                    targetClass, "matchVersion",
                    h5PageClass, Map::class.java, String::class.java,
                    XC_MethodReplacement.returnConstant(false)
                )
                Log.runtime(TAG, "✅ 延迟 Hook H5AppRpcUpdate 成功")
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ 延迟 Hook H5AppRpcUpdate 失败: ${t.message}")
            }

            // 2. Hook CDPBService
            try {
                val cdpbServiceClass = classLoader.loadClass("com.alipay.cdpb.api.CDPBService")
                val is3PTSpacesMethod = XposedHelpers.findMethodExactIfExists(cdpbServiceClass, "is3PTSpaces")
                if (is3PTSpacesMethod != null) {
                    XposedBridge.hookMethod(is3PTSpacesMethod, XC_MethodReplacement.returnConstant(false))
                    Log.runtime(TAG, "✅ 延迟 Hook CDPBService 成功")
                }
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ 延迟 Hook CDPBService 失败: ${t.message}")
            }

            // 3. Hook AlipayLogin 生命周期（状态监控）
            try {
                val loginActivityClass = classLoader.loadClass(General.CURRENT_USING_ACTIVITY)
                XposedHelpers.findAndHookMethod(loginActivityClass, "onResume", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager.setAlipayLoginActive(true)
                    }
                })
                XposedHelpers.findAndHookMethod(loginActivityClass, "onDestroy", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        fansirsqi.xposed.sesame.hook.lifecycle.LifecycleManager.setAlipayLoginActive(false)
                    }
                })
                Log.runtime(TAG, "✅ 延迟 Hook AlipayLogin 生命周期成功")
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ 延迟 Hook AlipayLogin 生命周期失败: ${t.message}")
            }

            // 4. Hook Runtime.loadLibrary0 防止 UnsatisfiedLinkError 崩溃
            try {
                XposedHelpers.findAndHookMethod(
                    java.lang.Runtime::class.java, "loadLibrary0",
                    ClassLoader::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.hasThrowable() && param.throwable is UnsatisfiedLinkError) {
                                val libName = param.args[1] as? String ?: return
                                val t = param.throwable
                                Log.runtime(TAG, "捕获到 Runtime.loadLibrary0 异常 ($libName): ${t.message}")
                                try {
                                    val soName = if (libName.startsWith("lib") && libName.endsWith(".so")) {
                                        libName
                                    } else {
                                        "lib$libName.so"
                                    }
                                    val loader = param.args[0] as? ClassLoader
                                    val context = fansirsqi.xposed.sesame.hook.context.AppContext.getAppContext()
                                    if (context != null) {
                                        var loaded = false
                                        val needRetry = shouldRetryLibrary(libName)
                                        val maxRetries = if (needRetry) 5 else 1
                                        val sleepMs = if (needRetry) 200L else 0L

                                        for (retry in 1..maxRetries) {
                                            if (loadSoWithDependencies(context, soName, loader, null)) {
                                                loaded = true
                                                break
                                            }
                                            if (retry < maxRetries && sleepMs > 0) {
                                                try {
                                                    Thread.sleep(sleepMs)
                                                } catch (e: InterruptedException) {
                                                    break
                                                }
                                            }
                                        }
                                        if (loaded) {
                                            param.throwable = null
                                            return
                                        }
                                    }
                                } catch (ex: Throwable) {
                                    Log.runtime(TAG, "手动载入 JNI 库及依赖失败: ${ex.message}")
                                }
                            }
                        }
                    }
                )
                Log.runtime(TAG, "✅ Hook Runtime.loadLibrary0 成功")
            } catch (t: Throwable) {
                Log.runtime(TAG, "❌ Hook Runtime.loadLibrary0 失败: ${t.message}")
            }
        }

        private fun loadSoWithDependencies(
            context: Context,
            soName: String,
            loader: ClassLoader?,
            referencingSoPath: String? = null,
            loadingSet: MutableSet<String> = mutableSetOf()
        ): Boolean {
            if (failedSoSet.contains(soName)) {
                return false
            }
            if (loadingSet.contains(soName)) {
                Log.runtime(TAG, "⚠️ 发现循环依赖: $soName, 终止加载")
                return false
            }
            loadingSet.add(soName)
            val soFile = findSoFile(context, soName, referencingSoPath)
            if (soFile == null) {
                Log.runtime(TAG, "未找到 JNI 库: $soName")
                failedSoSet.add(soName)
                loadingSet.remove(soName)
                return false
            }
            try {
                loadNativeLib(loader, soFile.absolutePath)
                Log.runtime(TAG, "✅ 手动成功载入 JNI 库: ${soFile.absolutePath}")
                loadingSet.remove(soName)
                return true
            } catch (t: UnsatisfiedLinkError) {
                val msg = t.message
                Log.runtime(TAG, "载入 JNI 库 $soName 失败: $msg")
                if (msg != null) {
                    var neededSo: String? = null
                    var parsedRefPath: String? = null

                    val libStart = msg.indexOf("library \"")
                    if (libStart != -1) {
                        val libEnd = msg.indexOf("\"", libStart + 9)
                        if (libEnd != -1) {
                            neededSo = msg.substring(libStart + 9, libEnd)
                        }
                    }

                    val neededByStart = msg.indexOf("needed by ")
                    if (neededByStart != -1) {
                        val pathStart = neededByStart + 10
                        val namespaceStart = msg.indexOf(" in namespace", pathStart)
                        parsedRefPath = if (namespaceStart != -1) {
                            msg.substring(pathStart, namespaceStart).trim()
                        } else {
                            msg.substring(pathStart).trim()
                        }
                    }

                    if (neededSo != null && neededSo != soName) {
                        Log.runtime(TAG, "🔍 发现依赖项: $neededSo (由 $parsedRefPath 发起), 尝试先加载依赖项...")
                        if (loadSoWithDependencies(context, neededSo, loader, parsedRefPath, loadingSet)) {
                            try {
                                loadNativeLib(loader, soFile.absolutePath)
                                Log.runtime(TAG, "✅ 依赖加载后成功载入 JNI 库: ${soFile.absolutePath}")
                                loadingSet.remove(soName)
                                return true
                            } catch (retryErr: Throwable) {
                                Log.runtime(TAG, "重试载入 JNI 库 $soName 失败: ${retryErr.message}")
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.runtime(TAG, "载入 JNI 库 $soName 异常: ${t.message}")
            }
            failedSoSet.add(soName)
            loadingSet.remove(soName)
            return false
        }

        private fun loadNativeLib(loader: ClassLoader?, path: String) {
            val method = load0Method
            if (loader != null && method != null) {
                try {
                    method.invoke(java.lang.Runtime.getRuntime(), loader, path)
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    val target = e.targetException
                    if (target is UnsatisfiedLinkError) {
                        throw target
                    } else if (target != null) {
                        throw target
                    } else {
                        throw e
                    }
                }
            } else {
                System.load(path)
            }
        }

        private fun findSoFile(context: Context, name: String, referencingSoPath: String? = null): java.io.File? {
            // 1. 优先检查发起加载的 .so 所在的同级目录
            if (referencingSoPath != null) {
                val refFile = java.io.File(referencingSoPath)
                val parentDir = refFile.parentFile
                if (parentDir != null && parentDir.exists()) {
                    val target = java.io.File(parentDir, name)
                    if (target.exists()) return target
                }
            }

            // 2. 检查 App 的系统原生 JNI 目录 (nativeLibraryDir)
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            if (nativeLibDir != null) {
                val target = java.io.File(nativeLibDir, name)
                if (target.exists()) return target
            }

            // 3. 检查 npatch 的 native 缓存根目录
            val cacheDir = context.cacheDir
            if (cacheDir != null) {
                val npatchDir = java.io.File(cacheDir, "npatch")
                if (npatchDir.exists()) {
                    val target = findSoFileInDirectory(npatchDir, name, 0, 4)
                    if (target != null) return target
                }
            }

            // 4. 检查 files/plugins 目录
            val filesDir = context.filesDir
            if (filesDir != null) {
                val pluginsDir = java.io.File(filesDir, "plugins")
                if (pluginsDir.exists()) {
                    val target = findSoFileInDirectory(pluginsDir, name, 0, 3)
                    if (target != null) return target
                }
            }

            return null
        }

        private fun findSoFileInDirectory(dir: java.io.File, name: String, currentDepth: Int, maxDepth: Int): java.io.File? {
            if (currentDepth > maxDepth) return null
            val files = dir.listFiles() ?: return null
            for (f in files) {
                if (f.isDirectory) {
                    val dirName = f.name
                    if (dirName == "databases" || dirName == "shared_prefs" || dirName == "h5" || dirName == "js") {
                        continue
                    }
                    val res = findSoFileInDirectory(f, name, currentDepth + 1, maxDepth)
                    if (res != null) return res
                } else if (f.name == name) {
                    return f
                }
            }
            return null
        }

        private fun isCalledFromUI(): Boolean {
            try {
                val stack = Thread.currentThread().stackTrace
                for (element in stack) {
                    val name = element.className ?: continue
                    if (name.contains("android.app.Activity") || 
                        name.contains("androidx.fragment.app") || 
                        name.contains("android.support.v4.app") || 
                        name.contains("FragmentManager") ||
                        name.contains("FragmentTransaction")) {
                        return true
                    }
                }
            } catch (t: Throwable) {
                // Ignore and safely default to false
            }
            return false
        }

        private fun shouldRetryLibrary(libName: String): Boolean {
            val name = libName.lowercase(Locale.US)
            return name.contains("bundle2h") || name.contains("homegridbase") || name.contains("crosser")
        }
    }
}
