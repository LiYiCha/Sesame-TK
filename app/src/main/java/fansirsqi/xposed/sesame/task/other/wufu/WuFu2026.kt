package fansirsqi.xposed.sesame.task.other.wufu

import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONObject

object WuFu2026 {
    private const val TAG = "五福2026"
    private const val TIME_SLEEP :Long = 3254  // 3254ms = 3.254s

    fun start() {
        try {
            handleMainTaskList()

            handGameTask()
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }


    private fun handleMainTaskList(){
        val result = WuFuRpc().queryMainTaskList()
        try {
            val res = JSONObject(result)
            if(res.optBoolean("success")){
                val data = res.optJSONObject("data")
                val taskGroupList = data.optJSONArray("taskGroupList")
                // 遍历任务组有三个大任务组
                for (i in 0 until taskGroupList.length()){
                    val taskGroup = taskGroupList.optJSONObject(i)
                    // 遍历任务组下的任务
                    val taskList = taskGroup.optJSONArray("taskList")
                    for (j in 0 until taskList.length()){
                        val task = taskList.optJSONObject(j)
                        // 跳过已完成的任务
                        val taskStatus = task.optString("taskStatus")
                        if("NOT_DONE" != taskStatus)continue

                        // 跳过不是浏览任务
                        val taskType = task.optString("taskType")
                        if ("BROWSE" != taskType)continue

                        val scene = task.optString("scene")
                        val taskId = task.optString("taskId")
                        val subShortTitle = task.optString("subShortTitle")
                        val taskResult = JSONObject(WuFuRpc().mainTaskFinish(scene,taskId))
                        if (taskResult.optBoolean("success")){
                            Log.other(TAG, "完成[${subShortTitle}]")
                        }else{
                            Log.error("完成[${subShortTitle}]失败: ${taskResult}")
                        }
                        CoroutineUtils.sleepCompat(TIME_SLEEP)
                    }
                }
            }
        }catch (e:Exception){
            Log.error("wufu2026.handleMainTaskList: ${e.message}")
        }
    }

    private fun handGameTask(){
        // 查询套马次数
        val num = getGameNum()
        if(num == 0){
            return
        }

        // 套马游戏开局
        for(i in 0 until num){
            val res = JSONObject(WuFuRpc().startGame())
            if(res.optBoolean("success")) {
                val data = res.optJSONObject("data")
                val roundId = data.optString("roundId","")
                if(roundId.isEmpty()){
                    continue
                }
                CoroutineUtils.sleepCompat(TIME_SLEEP)
                // 预提交
                WuFuRpc().preCommit(roundId)
                CoroutineUtils.sleepCompat(TIME_SLEEP)
                // 最终提交
                val finishCommit = JSONObject(WuFuRpc().finishCommit(roundId))
                if(finishCommit.optBoolean("success")){
                    Log.other(TAG, "第[ $i ]完成套马")
                }else{
                    Log.error("套马失败: ${finishCommit}")
                }
            }else{
                Log.error("套马游戏开局失败: ${res}")
            }
        }
    }

    // 查询套马次数
    private fun getGameNum(): Int{
        val res = JSONObject(WuFuRpc().queryGameInfo())
        if(res.optBoolean("success")){
            val data = res.optJSONObject("data")
            val num = data.optInt("remainingLassoNum",0)
            return num
        }
        return 0
    }
}