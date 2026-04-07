package com.scaffold.update.checker

import com.google.gson.annotations.SerializedName

/**
 * 更新检查请求的响应模型。
 *
 * 此数据类表示在检查应用更新后从后端更新 API 接收的响应负载。
 * 它包含有关更新可用性、版本详细信息和下载信息。
 *
 * 需求：
 * - 2.1: 将 JSON 响应解析为 Kotlin 数据类
 * - 2.2: 提取 updateAvailable 布尔字段
 * - 2.3: 提取 latestVersion 和 currentVersion 字符串字段
 * - 2.4: 提取 forceUpdate 布尔字段
 * - 2.5: 提取 releaseNotes 字符串字段
 * - 2.6: 提取 downloadUrl 字符串字段
 */
data class UpdateCheckResponse(
    /**
     * 指示应用是否有可用更新。
     * 此字段始终存在于响应中。
     */
    @SerializedName("updateAvailable")
    val updateAvailable: Boolean,

    /**
     * 服务器上可用的最新版本。
     * 如果没有可用更新或未提供该字段，则为 null。
     * 示例："2.0.0"
     */
    @SerializedName("latestVersion")
    val latestVersion: String?,

    /**
     * 服务器识别的当前版本。
     * 如果未提供该字段，则为 null。
     * 示例："1.0.0"
     */
    @SerializedName("currentVersion")
    val currentVersion: String?,

    /**
     * 指示此更新是否为强制更新。
     * 当为 true 时，应用应要求用户在继续之前更新。
     * 此字段始终存在于响应中。
     */
    @SerializedName("forceUpdate")
    val forceUpdate: Boolean,

    /**
     * 描述最新版本新功能的发布说明。
     * 如果未提供发布说明，则为 null。
     * 可能包含 markdown 或纯文本。
     */
    @SerializedName("releaseNotes")
    val releaseNotes: String?,

    /**
     * 可以下载更新文件的 URL。
     * 如果没有可用更新或未提供该字段，则为 null。
     * 示例："https://example.com/updates/app-v2.0.0.apk"
     */
    @SerializedName("downloadUrl")
    val downloadUrl: String?,

    /**
     * 文件大小（字节）。
     */
    @SerializedName("fileSize")
    val fileSize: Long?,

    /**
     * 文件的 MD5 校验和。
     */
    @SerializedName("md5")
    val md5: String?,

    /**
     * 文件的 SHA256 校验和。
     */
    @SerializedName("sha256")
    val sha256: String?,

    /**
     * 更新文件列表，包含详细的文件信息。
     */
    @SerializedName("files")
    val files: List<UpdateFile>?
)

/**
 * 更新文件信息。
 */
data class UpdateFile(
    /**
     * 文件类型，例如 "installer"。
     */
    @SerializedName("fileType")
    val fileType: String,

    /**
     * 文件名。
     */
    @SerializedName("fileName")
    val fileName: String,

    /**
     * 文件键，用于通过 /api/file/download/{fileKey} 下载文件。
     */
    @SerializedName("fileKey")
    val fileKey: String,

    /**
     * 文件大小（字节）。
     */
    @SerializedName("fileSize")
    val fileSize: Long,

    /**
     * 下载 URL（相对路径）。
     */
    @SerializedName("downloadUrl")
    val downloadUrl: String,

    /**
     * 文件的 MD5 校验和。
     */
    @SerializedName("md5")
    val md5: String,

    /**
     * 文件的 SHA256 校验和。
     */
    @SerializedName("sha256")
    val sha256: String
)
