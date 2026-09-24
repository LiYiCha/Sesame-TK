package fansirsqi.xposed.sesame.hook.rpc

import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.IdMapManager
import fansirsqi.xposed.sesame.util.maps.UserMap
import fansirsqi.xposed.sesame.util.maps.VipDataIdMap

/**
 * RPC 请求令牌抓取器
 * 在真实 App 发起庄园广告 RPC 时捕获 referToken 保存，供抽抽乐广告任务 RPC 完成时复用
 */
object TokenHooker {
    private const val TAG = "TokenHooker"

    /** 蚂蚁庄园广告任务 RPC，用户真实观看广告时客户端会携带 referToken */
    private const val METHOD_XLIGHT_PLUGIN = "com.alipay.adexchange.ad.facade.xlightPlugin"
    private const val KEY_ANT_FARM_REFER_TOKEN = "AntFarmReferToken"

    /** RPC 拦截点回调：method 为 OperationType，args 为本次请求的参数对象数组 */
    fun handleRpc(method: String, args: Array<Any?>) {
        if (method != METHOD_XLIGHT_PLUGIN) return
        handleAntFarmToken(args)
    }

    /**
     * 提取请求参数链 positionRequest.referInfo.referToken 并保存
     * 字段名为服务端序列化契约（通常未混淆），反射读取失败则静默跳过
     */
    private fun handleAntFarmToken(args: Array<Any?>) {
        try {
            val token = extractReferToken(args) ?: return
            val vipData = IdMapManager.getInstance(VipDataIdMap::class.java)
            val uid = UserMap.currentUid
            vipData.load(uid)
            // token 未变化不重复写盘
            if (vipData.get(KEY_ANT_FARM_REFER_TOKEN) == token) return
            vipData.add(KEY_ANT_FARM_REFER_TOKEN, token)
            if (vipData.save(uid)) {
                Log.farm(TAG, "捕获到蚂蚁庄园 referToken 并已保存, uid=$uid")
            } else {
                Log.error(TAG, "保存 vipdata.json 失败, uid=$uid")
            }
        } catch (e: Exception) {
            Log.error(TAG, "解析 referToken 异常: ${e.message}")
        }
    }

    private fun extractReferToken(args: Array<Any?>): String? {
        for (arg in args) {
            if (arg == null) continue
            val positionRequest = readMember(arg, "positionRequest") ?: continue
            val referInfo = readMember(positionRequest, "referInfo") ?: continue
            val token = readMember(referInfo, "referToken") as? String
            if (!token.isNullOrBlank()) return token
        }
        return null
    }

    /** 优先反射字段，失败再尝试 getter */
    private fun readMember(obj: Any, name: String): Any? {
        return try {
            XposedHelpers.getObjectField(obj, name)
        } catch (_: Throwable) {
            try {
                XposedHelpers.callMethod(obj, "get" + name.replaceFirstChar { it.uppercaseChar() })
            } catch (_: Throwable) {
                null
            }
        }
    }
}
