package fansirsqi.xposed.sesame.task.otherTask2.eleme

import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.task.ModelTask

/**
 * 饿了么管理模型（设置页独立卡片，与「饿了么」任务页并列于同一分组）
 *
 * 仅承载管理类入口：账号状态、兑换商城、设备UA、风控名单。
 * 字段实例仍定义在 [ElemeTask] companion 中（单例共享，运行时读写引用不变），
 * 本模型仅决定这些字段在设置界面中展示于哪个页面。
 *
 * 注意：字段从 ElemeTask 拆分到本模型后，配置 JSON 中的存储键由 "ElemeTask"
 * 变为 "ElemeManageTask"；旧配置的迁移逻辑见 [fansirsqi.xposed.sesame.data.Config.setModelFieldsMap]。
 */
class ElemeManageTask : ModelTask() {

    override fun getName(): String = "饿了么管理"

    override fun getGroup(): ModelGroup = ModelGroup.ELEME

    override fun getIcon(): String = "Default.png"

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(ElemeTask.elemeAccountStatus)
        modelFields.addField(ElemeTask.elemeRefreshMall)
        modelFields.addField(ElemeTask.elemeCustomUa)
        modelFields.addField(ElemeTask.elemeRiskTasks)
        return modelFields
    }

    /** 纯管理页模型，不承担任何定时任务，永不调度执行 */
    override fun check(): Boolean = false

    /** 空实现：本模型没有任何任务逻辑 */
    override fun runJava() {
    }
}
