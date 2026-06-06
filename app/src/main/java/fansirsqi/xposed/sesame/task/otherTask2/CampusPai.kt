package fansirsqi.xposed.sesame.task.otherTask2

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.RandomUtil
import fansirsqi.xposed.sesame.util.TimeUtil
import org.json.JSONObject

/**
 * 校园派|签到、任务、一键领取
 */
class CampusPai {
    private val TAG = "校园派 🏫"

    fun handle() {
        val hours = TimeUtil.getHourOfDay()
        if (hours < 7) {
            return
        }
        //检测人群
        checkCrowd()
        //签到
        if (!Status.hasFlagToday("CampusPai_SignIn")) {
            queryCheckInStatus()
        }
        //领取
        doGet()
        //查询用户信息
        if (!Status.hasFlagToday("CampusPai_queryUserPaiCoin")) {
            doQuery()
        }
    }
    fun campusPaiTask(){
        //做任务
        for (i in 1..3){
            if(!doTask()){
                break
            }
            doSleep()
        }
        doGet()
    }

    private fun doSignIn() {
        val method = "alipay.anteduprod.campus.cycleSignInfo.periodSignIn"
        val params = "[{\"signRewardType\":\"PAI_POINT\",\"signScene\":\"OPEN_SEASON\"}]"
        try{
            val response = JSONObject(RequestManager.requestString(method, params))
            if(response.optBoolean("success")){
                val paiCoinQuantity = response.optInt("paiCoinQuantity",0)
                Log.other(TAG, "✅ 签到成功,获得"+paiCoinQuantity+"币")
                Status.setFlagToday("CampusPai_SignIn")
            }else{
                Log.error(TAG, "doSignIn签到失败:$response")
            }
        }catch (e: Exception){
            Log.error(TAG, "doSignIn签到失败:$e")
        }
    }
    private fun queryTaskList(){
        val method = "com.alipay.promofrontcenter.play.query"
        val params = "[{\"playCodes\":[{\"actionType\":\"TaskCenterPlayUserApi.query\",\"extInfo\":{\"extInfo\":" +
                "{\"componentCode\":\"a1296.b72015.ca55046\"}},\"playCode\":\"promo-school-activty_task\"}]}]"
        try{
            val response = JSONObject(RequestManager.requestString(method, params))
            if(response.optBoolean("success")){
                Log.other(TAG, "queryTaskList成功:$response")
            }else{
                Log.error(TAG, "queryTaskList失败:$response")
            }
        }catch (e: Exception){
            Log.error(TAG, "queryTaskList失败:$e")
        }
    }
    private fun doTask() :Boolean {
        val biz = doGetAd()
        if(biz.isNullOrEmpty()){
            return false
        }
        TimeUtil.sleep(RandomUtil.nextLong(15000, 16000))
        val method = "com.alipay.adtask.biz.mobilegw.service.task.finish"
        val params = "[{\"bizId\":\"$biz\",\"extendInfo\":{}}]"
        try{
            val response = JSONObject(RequestManager.requestString(method, params))
            if(response.optBoolean("success")){
                Log.other(TAG, "✅ 看广告任务成功")
                return true
            }else{
                Log.error(TAG, "doTask失败:$response")
            }
        }catch (e: Exception){
            Log.error(TAG, "doTask失败:$e")
        }
        return false
    }
    /**
     * 一键领取
     */
    private fun doGet() {
        val method0 = "alipay.anteduprod.campusstyle.queryCertificateSendOrder"
        val params0 = "[{\"campId\":\"CP171902118\"}]"
        val method = "alipay.anteduprod.campusstyle.batchConsumeCert"

        var certTemplateId: String? = null
        var consumeNum: Int = 0
        var price: Int = 0
        var totalPrice: Int = 0

        try{
            val response0 = JSONObject(RequestManager.requestString(method0, params0))
            if(response0.optBoolean("success")){
                val certTemplateInfoVos = response0.optJSONArray("certTemplateInfoVos")
                if (certTemplateInfoVos != null && certTemplateInfoVos.length() > 0) {
                    val date0 = certTemplateInfoVos.optJSONObject(0)
                    certTemplateId = date0.optString("certTemplateId")
                    consumeNum = date0.optInt("consumeNum", 0)
                    price = date0.optInt("price", 0)
                    totalPrice = response0.optInt("totalPrice", 0)
                }
            }
            if(certTemplateId.isNullOrEmpty() || totalPrice == 0){
                //Log.runtime(TAG, "没有可领取的派币")
                return
            }
            val params = "[{\"certTemplateInfoVos\":[{\"certTemplateId\":\"$certTemplateId\",\"consumeNum\":$consumeNum,\"price\":$price}]}]"

            val response = JSONObject(RequestManager.requestString(method, params))
            if(response.optBoolean("success")){
                Log.other(TAG, "💰一键领取派币 +${totalPrice}币")
            }else{
                Log.error(TAG, "一键领取派币失败:$response")
            }
        }catch (e: Exception){
            Log.error(TAG, "一键领取派币失败:$e")
        }
    }

    /**
     * 查询用户信息
     */
    private fun doQuery() {
        if(Status.hasFlagToday("CampusPai_queryUserPaiCoin")){
            return
        }
        val method = "alipay.anteduprod.campusstyle.queryUserPaiCoin"
        val params = "[{}]"
        try{
            val response = JSONObject(RequestManager.requestString(method, params))
            if(response.optBoolean("success")){
                val pointAccountInfo = response.optJSONObject("pointAccountInfo")
                val availableCashAmount = pointAccountInfo?.optString("availableCashAmount", "0")
                Log.other(TAG, "💰 当前余额:$availableCashAmount")
            }else{
                Log.error(TAG, "查询用户信息失败:$response")
            }
            Status.setFlagToday("CampusPai_queryUserPaiCoin")
        }catch (e: Exception){
            Log.error(TAG, "查询用户信息失败:$e")
        }
    }

    //查询签到状态
    private fun queryCheckInStatus() {
        //查询余额
        doQuery()
        //休眠
        doSleep()
        //查询签到状态
        val method = "alipay.anteduprod.campus.cycleSignInfo.queryCurrentPeriod"
        val params = "[{\"signScene\":\"OPEN_SEASON\"}]"
        try{
            val response = JSONObject(RequestManager.requestString(method, params))
            if(response.optBoolean("success")){
                val hadCheckIn = response.optBoolean("hadCheckIn")
                Log.other(TAG, "📅 签到状态:$hadCheckIn")
                if(hadCheckIn){
                    Status.setFlagToday("CampusPai_SignIn")
                }else{
                    doSignIn()
                }
            }else{
                Log.error(TAG, "queryCheckInStatus失败:$response")
            }
            Status.setFlagToday("CampusPai_SignIn")
        }catch (e: Exception){
            Log.error(TAG, "queryCheckInStatus失败:$e")
        }
    }

    //检测人群
    private fun checkCrowd(){
        if(Status.hasFlagToday("CampusPai_checkCrowd")){
            return
        }
        val method = "alipay.anteduprod.generalactivity.crowd.checkCrowd"
        val params = "[{\"checkScene\":\"CY25_MAKE_PENG_FRIEND_ACCESS_LIST\"}]"
        try{
            val response = JSONObject(RequestManager.requestString(method, params))
            if(response.optBoolean("success")){
                // 解析响应字段
                val crowdCheckSwitch = response.optBoolean("crowdCheckSwitch", false)
                val inCrowd = response.optBoolean("inCrowd", false)
                val showType = response.optInt("showType", 0)

                // 记录人群状态信息
                Log.runtime(TAG, "👥人群校验开关:$crowdCheckSwitch, 用户在目标人群中:$inCrowd, 显示类型:$showType")

                // 根据观察，即使inCrowd为false也能参与活动，所以这里只记录信息不阻断流程
                if (!crowdCheckSwitch) {
                    Log.runtime(TAG, "⚠️人群校验开关已关闭")
                }
            }else{
                Log.error(TAG, "checkCrowd失败:$response")
            }
        }catch (e: Exception){
            Log.error(TAG, "checkCrowd失败:$e")
        }finally {
            Status.setFlagToday("CampusPai_checkCrowd")
        }
    }



    //获取广告
    private fun doGetAd(): String? {
        if (Status.hasFlagToday("CampusPai_GetAdTask")) {
            return null
        }

        val method = "com.alipay.adexchange.ad.facade.xlightPlugin"
        val params = "[{\"positionRequest\":{\"referInfo\":{},\"rtaExtMap\":{},\"spaceCode\":\"XIAOYUANPAI_TASK\"}," +
                "\"sdkPageInfo\":{\"pageFrom\":\"ch_xiaoyuanpaiicon__chsub_xiaoyuanpaipaibiicon\"," +
                "\"pageUrl\":\"https://render.alipay.com/p/yuyan/180020010001259828/signIn.html?caprMode=sync\"," +
                "\"unionAppId\":\"2060090000308792\",\"xlightSDKType\":\"ADSDK-H5-H5_COMMOM\"}}]"

        try {
            val responseStr = RequestManager.requestString(method, params)
            val response = JSONObject(responseStr)

            // 改进状态判断逻辑：success为true，或retCode为"0"，或errorMsg为"ok"，或adList存在且不为空
            val hasAdList = response.has("adList") && (response.optJSONArray("adList")?.length() ?: 0) > 0
            val isSuccess = response.optBoolean("success", false) ||
                    "0" == response.optString("retCode") ||
                    "ok" == response.optString("errorMsg") ||
                    hasAdList

            if (isSuccess) {
                // 检查是否有adList字段
                if (!response.has("adList")) {
                    Status.setFlagToday("CampusPai_GetAdTask")
                    return null
                }

                val adList = response.optJSONArray("adList")
                if (adList == null || adList.length() == 0) {
                    Status.setTemporaryStatusWithExpiry("CampusPai_GetAdTask", 1000 * 60 * 60)
                    return null
                }

                // 遍历所有广告项，寻找合适的任务
                for (i in 0 until adList.length()) {
                    val data = adList.optJSONObject(i)
                    if (data != null) {
                        val bizId = data.optString("xlightBizId")
                        val schemaJsonStr = data.optString("schemaJson")

                        if (schemaJsonStr.isNotEmpty()) {
                            try {
                                val schemaJson = JSONObject(schemaJsonStr)
                                val rewardPrice = schemaJson.optString("taskRewardPrice", "0")
                                val taskTitle = schemaJson.optString("taskMainTitle", "未知任务")

                                Log.other(TAG, "发现广告任务: $taskTitle, 奖励: $rewardPrice 币")
                                if (bizId.isNotEmpty()) {
                                    return bizId
                                }
                            } catch (e: Exception) {
                                Log.error(TAG, "解析广告任务schema失败: $e")
                            }
                        } else {
                            // 即使没有schemaJson，如果有bizId也可以尝试使用
                            if (bizId.isNotEmpty()) {
                                return bizId
                            }
                        }
                    }
                }

                // 如果没有找到合适的任务
                Status.setFlagToday("CampusPai_GetAdTask")
                return null
            } else {
                Status.setFlagToday("CampusPai_GetAdTask")
            }
        } catch (e: Exception) {
            Log.error(TAG, "doGetAd失败: $e")
        }

        return null
    }



    private fun doSleep(){
        try {
            val sleepTime = RandomUtil.nextLong(1000, 3000)
            if (sleepTime > 0) {
                TimeUtil.sleep(sleepTime)
            } else {
                TimeUtil.sleep(2000L) // 默认休眠2秒
            }
        } catch (e: Exception) {
            TimeUtil.sleep(5000L) // 出现异常时默认休眠5秒
        }
    }

}