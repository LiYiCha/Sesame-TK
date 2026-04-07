package com.scaffold.update.checker

import com.google.gson.annotations.SerializedName

/**
 * 与后端 Result<T> 结构匹配的通用 API 响应包装器。
 *
 * 此数据类包装来自后端的所有 API 响应，为处理成功和错误响应提供一致的结构。
 * 后端对所有端点都返回此格式的响应。
 *
 * 需求：
 * - 2.1: 将 JSON 响应解析为 Kotlin 数据类
 *
 * @param T 响应中包含的数据类型
 */
data class ApiResponse<T>(
    /**
     * 响应状态码。
     * 通常表示 HTTP 状态码或自定义应用代码。
     * 示例：200 表示成功，404 表示未找到等。
     */
    @SerializedName("code")
    val code: Int,
    
    /**
     * 描述结果的响应消息。
     * 提供有关响应状态的可读信息。
     * 示例："Success"、"Update available"、"No updates found"
     */
    @SerializedName("message")
    val message: String,
    
    /**
     * 实际的响应数据负载。
     * 包含由 API 端点返回的类型化数据。
     * 如果响应不包含数据或发生错误，则为 null。
     */
    @SerializedName("data")
    val data: T?,

    @SerializedName("success")
    val success: Boolean,
    @SerializedName("timestamp")
    val timestamp: Long?
)
