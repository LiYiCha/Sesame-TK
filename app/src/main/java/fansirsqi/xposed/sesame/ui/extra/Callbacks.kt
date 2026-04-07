package fansirsqi.xposed.sesame.ui.extra

/**
 * 与 RPC 调试界面交互的回调
 */
interface Callbacks {
    fun onSend(id: Int)
    fun onEdit(id: Int)
    fun onDelete(id: Int)
    fun onDuplicate(id: Int)
    fun onToggle(id: Int)
}
