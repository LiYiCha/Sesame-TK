package com.updater.model

import java.io.Serializable

enum class UpdateSourceType {
    CLOUDFLARE_R2,     // 基于 Cloudflare Pages + R2 网盘 API
    GITHUB_RELEASES    // 基于 GitHub Releases API / 镜像直链
}

data class UpdateSource(
    val id: String,                    // 唯一标识 (ID)
    var name: String,                  // 显示名称，如 "Cloudflare 官方源", "GitHub Releases 官方源"
    var url: String,                   // 接口地址或仓库地址
    var type: UpdateSourceType,        // 更新源类型
    var downloadHost: String? = null,  // 可选：独立下载加速域名 (CDN)
    val isPreset: Boolean = false      // 是否为系统内置预设源（不可随意删除）
) : Serializable
