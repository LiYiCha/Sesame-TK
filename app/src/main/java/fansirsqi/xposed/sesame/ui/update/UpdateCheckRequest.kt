package com.scaffold.update.checker

import com.google.gson.annotations.SerializedName
import java.util.Locale

/**
 * 检查应用更新的请求模型。
 *
 * 此数据类表示发送到后端更新 API 的请求负载，用于检查应用是否有新版本可用。
 *
 * 需求：
 * - 1.1: 在请求正文中包含 appId、currentVersion、platform、channel 和 locale
 * - 1.2: platform 字段使用 "android" 值
 * - 1.3: channel 未指定时默认为 "stable"
 * - 1.4: locale 未指定时默认为设备的系统区域设置
 */
data class UpdateCheckRequest(
    /**
     * 应用的唯一标识符。
     * 示例："com.example.myapp"
     */
    @SerializedName("appId")
    val appId: String,
    
    /**
     * 设备上安装的应用当前版本。
     * 示例："1.0.0"
     */
    @SerializedName("currentVersion")
    val currentVersion: String,
    
    /**
     * 平台标识符。对于 Android 应用，始终为 "android"。
     */
    @SerializedName("platform")
    val platform: String = "android",
    
    /**
     * 更新的分发渠道。
     * 默认为 "stable"。其他值可能包括 "beta"、"alpha" 等。
     */
    @SerializedName("channel")
    val channel: String = "stable",
    
    /**
     * 设备的区域设置/语言代码。
     * 默认为系统的默认区域设置语言代码。
     * 示例："en"、"es"、"fr"
     */
    @SerializedName("locale")
    val locale: String = Locale.getDefault().language
)
