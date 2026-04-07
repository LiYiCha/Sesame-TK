package fansirsqi.xposed.sesame.task.exchange

/**
 * 青春特权支持接口
 */
interface YouthPrivilegeSupport {
    /**
     * 获取选中的青春特权名称列表
     * 返回类型为 List 以保持顺序
     */
    fun getYouthPrivilegeSelectedNames(): List<String>
}
