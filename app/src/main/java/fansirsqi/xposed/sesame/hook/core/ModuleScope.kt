package fansirsqi.xposed.sesame.hook.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

/**
 * 模块级的协程作用域，用于管理 Xposed 模块生命周期内的异步任务
 */
object ModuleScope : CoroutineScope {
    private val moduleJob = SupervisorJob()
    override val coroutineContext = moduleJob + Dispatchers.IO
    
    /**
     * 取消所有正在运行的任务
     */
    fun cancelAll() {
        moduleJob.cancelChildren()
    }
}
