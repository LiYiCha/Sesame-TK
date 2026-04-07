package fansirsqi.xposed.sesame.model

import fansirsqi.xposed.sesame.task.AnswerAI.AnswerAI
import fansirsqi.xposed.sesame.task.ancientTree.AncientTree
import fansirsqi.xposed.sesame.task.antCooperate.AntCooperate
import fansirsqi.xposed.sesame.task.antDodo.AntDodo
import fansirsqi.xposed.sesame.task.antFarm.AntFarm
import fansirsqi.xposed.sesame.task.antForest.AntForest
import fansirsqi.xposed.sesame.task.antMember.AntMember
import fansirsqi.xposed.sesame.task.antOcean.AntOcean
import fansirsqi.xposed.sesame.task.antOrchard.AntOrchard
import fansirsqi.xposed.sesame.task.antSports.AntSports
import fansirsqi.xposed.sesame.task.antStall.AntStall
import fansirsqi.xposed.sesame.task.exchange.FlashSaleModule
import fansirsqi.xposed.sesame.task.greenFinance.GreenFinance
import fansirsqi.xposed.sesame.task.other.OtherTask0
import fansirsqi.xposed.sesame.task.otherTask.OtherTask
import fansirsqi.xposed.sesame.task.otherTask2.OtherTask2
import fansirsqi.xposed.sesame.task.reserve.Reserve
import fansirsqi.xposed.sesame.task.welfareCenter.WelfareCenter

object ModelOrder {
    private val array = arrayOf(
        BaseModel::class.java ,
                //基础设置
                AntForest::class.java ,
                //森林
                AntFarm ::class.java ,
                //庄园
                AntOrchard ::class.java ,
                //农场
                FlashSaleModule ::class.java ,
                //兑换任务
                OtherTask ::class.java ,
                //其他任务
                OtherTask2 ::class.java ,
                //其他任务2
                OtherTask0::class.java ,
                // 其他任务0
                WelfareCenter ::class.java ,
                //福利中心
                AntMember ::class.java ,
                //会员
                AntOcean ::class.java ,
                //海洋
                AntDodo ::class.java ,
                //神奇物种
                AncientTree ::class.java ,
                //古树
                AntCooperate ::class.java ,
                //合种
                Reserve ::class.java ,
                //保护地
                AntSports ::class.java ,
                //运动
                AntStall ::class.java ,
                //蚂蚁新村
                GreenFinance ::class.java,
                //绿色经营
                AnswerAI ::class.java //AI答题
        //            AntBookRead.class,//读书
        //            ConsumeGold.class,//消费金
        //            OmegakoiTown.class,//小镇,


    )

    val allConfig: List<Class<out Model>> = array.toList()
}
