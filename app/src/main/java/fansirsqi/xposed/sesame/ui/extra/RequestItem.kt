package fansirsqi.xposed.sesame.ui.extra

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 请求数据项
 * @param id 请求ID
 * @param title 请求标题
 * @param description 请求描述（可选）
 * @param method 请求方法
 * @param data 请求数据
 * @param expanded 是否展开（默认不展开）
 */
data class RequestItem @JsonCreator constructor(
    @JsonProperty("id") val id: Int = 0,
    @JsonProperty("title") var title: String = "",
    @JsonProperty("description") var description: String = "",
    @JsonProperty("method") var method: String = "",
    @JsonProperty("data") var data: String = "",
    @JsonProperty("expanded") var expanded: Boolean = false
) {
    constructor(title: String, method: String, data: String) : this(0, title, "", method, data, false)

    // Compose 扩展函数
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "title" to title,
        "description" to description,
        "method" to method,
        "data" to data,
        "expanded" to expanded
    )

    companion object {
        fun fromMap(map: Map<String, Any>): RequestItem = RequestItem(
            id = map["id"] as Int,
            title = map["title"] as String,
            description = (map["description"] as? String) ?: "",
            method = map["method"] as String,
            data = map["data"] as String,
            expanded = map["expanded"] as Boolean
        )
    }
}