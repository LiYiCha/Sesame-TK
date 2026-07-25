package fansirsqi.xposed.sesame.task.otherTask

import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 天天来财 | 喂，金豆任务
 */
class GoldBean {
    private val TAG = "天天来财💫"
    //活动id
    private var activityId = "AC2025060700000569488"
    fun run(){
        val hour = TimeUtil.getHourOfDay()
        if(hour<7){
            return
        }
        //初始化
        if (init()) {
            //处理任务
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    handleTask()
                } catch (e: Exception) {
                    Log.error(TAG, "handleTask error: ${e}")
                }
            }
        }

    }

    private fun init(): Boolean{
        try {
            val queryHome = queryHome()
            if(queryHome.optBoolean("success")){
                val data = queryHome.optJSONObject("data")
                if (data != null) {
                    // 处理号码使用
                    handleUseFood(data)
                    // 获取活动id
                    activityId = data.optString("activityId","")
                }else{
                    return false
                }
                taskRecallWidget()
                taskRecallGoldbean()
                behaviorFatigueCheck()
                behaviorFatigueCheck2()
                easterEggQuery()
                //revistTaskQuery()
                activityProcessQuery()
            }else{
                Log.error(TAG, "查询主页失败:${queryHome}")
                return false
            }
        } catch (e: Exception) {
            Log.error(TAG, "init error: ${e}")
            return false
        }
        return true
    }


    private suspend fun handleTask(){
        val recallGoldbean = taskRecallGoldbean()
        if(recallGoldbean.optBoolean("success")){
            val data = recallGoldbean.optJSONObject("data")
            if (data==null||data.isNull("tasks")) {
                return
            }
            val tasks = data.optJSONArray("tasks")
            if (tasks != null) {
                for (i in 0 until tasks.length()){
                    val task = tasks.getJSONObject(i)
                    // 筛选出浏览任务
                    val taskType = task.optString("taskType")
                    if (!taskType.equals("WEALTH_BROWSE")){
                        continue
                    }
                    // 跳过已完成
                    val status = task.optString("status")
                    if(status.equals("DONE")){
                        continue
                    }
                    val needSignUp = task.optBoolean("needSignUp")
                    val taskCenInfo = task.optString("taskCenInfo")
                    val taskCenterId = task.optString("taskCenterId")
                    val taskId = task.optString("taskId")
                    //领取任务
                    if (needSignUp){
                        trigger(taskId,taskCenInfo)
                    }
                    delay(15000 + (0..1000).random().toLong() )
                    //完成任务
                    val result = triggerRecall(taskCenterId, taskId, taskCenInfo)
                    val title = task.optString("title")
                    if (result.optBoolean("success")){
                        val goldBeanCount = task.optInt("goldBeanCount")
                        Log.other(TAG, "完成[$title]+$goldBeanCount 金豆")
                    }else{
                        Log.other(TAG, "完成[$title]失败:$result")
                    }
                    delay(1000 + (0..1000).random().toLong() )
                }
            }
        }
    }

    private fun handleUseFood(data: JSONObject){
        val piXiuFoods = data.optJSONArray("piXiuFoods")
        for (i in 0 until piXiuFoods.length()){
            val piXiuFood = piXiuFoods.getJSONObject(i)
            val status = piXiuFood.optString("status")
            //跳过已经使用的号码
            if(!status.equals("UNUSED")){
                continue
            }
            val id = piXiuFood.optString("id")
            val foodUse = foodUse(id)
            if (foodUse.optBoolean("success")){
                Log.other(TAG, "领取号码成功")
            }
            propsQueryV2()
            userState()
        }
    }

    private fun queryHome(): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.homepage.query"
        val params = "[{\"init\":true}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    private fun taskRecallWidget(): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.widget.task.recall"
        val params = "[{\"context\":{\"collectStatus\":\"COLLECTED\"}}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //获取任务列表
    private fun taskRecallGoldbean(): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.goldbean.task.recall"
        val params = "[{\"activityId\":\"$activityId\",\"context\":{\"collectStatus\":\"COLLECTED\"}}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    private fun behaviorFatigueCheck(): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.behavior.fatigue.check"
        val params = "[{\"activityId\":\"$activityId\",\"ruleIds\":[\"1D_1T_SHOW_CONSERVATION\"],\"sceneCode\":\"TODAY_FIRST_PAT_GUIDE_TIRED\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    private fun behaviorFatigueCheck2(): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.behavior.fatigue.check"
        // 使用号码后的行为处理
        val params = "[{\"activityId\":\"$activityId\",\"ruleIds\":[\"1D_2T_SHOW_CONSERVATION\"],\"sceneCode\":\"PIXIU_PROPS_GUIDE\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    private fun easterEggQuery(): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.easterEgg.query"
        val params = "[{\"activityId\":\"$activityId\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    private fun revistTaskQuery(): JSONObject{
        val method = "alipay.openservice.yao.yaoyy.revist.task.query"
        val params = "[{\"appId\":\"2060090000317135\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    private fun activityProcessQuery(): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.activity.process.query"
        val params = "[{\"activityId\":\"$activityId\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //使用号码
    private fun foodUse(foofId: String): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.pixiu.food.use"
        val params = "[{\"activityId\":\"$activityId\",\"piXiuFoodIds\":[\"$foofId\"]}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    private fun propsQueryV2(): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.props.query.v2"
        val params = "[{\"activityId\":\"$activityId\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    private fun userState(): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.user.state"
        val params = "[{\"activityId\":\"$activityId\",\"option\":{\"calendarReminderGuide\":true," +
                "\"eggFinishGuide\":true,\"goldenBeanPropsGuide\":true,\"propQueryGuide\":true," +
                "\"universalNumberGuide\":true}}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //领取任务
    private fun trigger(appletId:String,taskCenInfo:String): JSONObject{
        val method = "alipay.promoprod.applet.trigger"
        val params = "[{\"activityId\":\"$activityId\",\"appletId\":\"$appletId\"," +
                "\"stageCode\":\"signup\",\"taskCenInfo\":\"$taskCenInfo\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //完成任务
    private fun triggerRecall(taskCenterId: String,appletId:String,taskCenInfo:String): JSONObject{
        val method = "alipay.ofpgrowth.ttlc.task.recall.trigger"
        val params = "[{\"taskCenInfo\":\"$taskCenInfo\"," +
                "\"taskCenterId\":\"$taskCenterId\",\"taskId\":\"$appletId\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
}