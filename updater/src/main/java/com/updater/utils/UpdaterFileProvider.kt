package com.updater.utils

import androidx.core.content.FileProvider

/**
 * 独立的更新模块专属 FileProvider
 * 避免与主工程的 FileProvider 类名与配置产生合并冲突
 */
class UpdaterFileProvider : FileProvider()
