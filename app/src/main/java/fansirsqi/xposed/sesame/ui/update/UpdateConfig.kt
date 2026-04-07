package fansirsqi.xposed.sesame.ui.update

/**
 * 更新配置
 *
 * @param baseUrl 服务器地址（必需）
 * @param appId 应用 ID（必需）
 * @param channel 更新渠道（默认 "beta"）
 * @param downloadDir 下载目录名称（默认 "Download"，相对于外部存储）
 * @param enableFileVerification 是否启用文件验证（默认 true）
 */
data class UpdateConfig(
    val baseUrl: String,
    val appId: String,
    val channel: String = "beta",
    val downloadDir: String = "Download",
    val enableFileVerification: Boolean = true
) {
    companion object {
        /**
         * 默认配置 - 用于 Sesame-TK 项目
         */
        val DEFAULT = UpdateConfig(
            baseUrl = "http://124.71.70.14:8085",
            appId = "fansirsqi.xposed.sesame",
            channel = "beta"
        )

        /**
         * 生产环境配置示例
         */
        fun production(baseUrl: String, appId: String) = UpdateConfig(
            baseUrl = baseUrl,
            appId = appId,
            channel = "stable"
        )
    }
}
