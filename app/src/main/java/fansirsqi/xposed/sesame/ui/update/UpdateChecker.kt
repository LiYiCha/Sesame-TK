package com.scaffold.update

import android.util.Log
import com.scaffold.update.checker.ApiResponse
import com.scaffold.update.checker.UpdateCheckRequest
import com.scaffold.update.checker.UpdateCheckResponse
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 更新检查器 - 纯业务逻辑，无 UI
 *
 * @param baseUrl 服务器地址
 * @param appId 应用 ID
 * @param currentVersion 当前版本
 * @param channel 更新渠道
 */
class UpdateChecker(
    private val baseUrl: String,
    private val appId: String,
    private val currentVersion: String,
    private val channel: String = "beta"
) {
    private val TAG = "UpdateChecker"
    private val updateService: UpdateService

    init {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        updateService = retrofit.create(UpdateService::class.java)
    }

    private interface UpdateService {
        @POST("/api/update/check")
        suspend fun checkForUpdate(@Body request: UpdateCheckRequest): Response<ApiResponse<UpdateCheckResponse>>

        @Streaming
        @GET("/api/file/download/{fileKey}")
        suspend fun downloadFile(@Path(value = "fileKey", encoded = true) fileKey: String): Response<ResponseBody>
    }

    /**
     * 检查更新
     * @return UpdateCheckResponse 或 null（失败时）
     */
    suspend fun checkForUpdate(): UpdateCheckResponse? {
        return try {
            Log.d(TAG, "检查更新，当前版本: $currentVersion")

            val request = UpdateCheckRequest(
                appId = appId,
                currentVersion = currentVersion,
                platform = "android",
                channel = channel
            )

            val response = updateService.checkForUpdate(request)

            if (!response.isSuccessful) {
                Log.e(TAG, "检查更新失败: HTTP ${response.code()}")
                return null
            }

            val apiResponse = response.body()
            if (apiResponse?.data == null) {
                Log.e(TAG, "响应数据为空")
                return null
            }

            Log.d(TAG, "检查更新成功，有更新: ${apiResponse.data.updateAvailable}")
            apiResponse.data

        } catch (e: Exception) {
            Log.e(TAG, "检查更新异常: ${e.message}", e)
            null
        }
    }

    /**
     * 下载文件
     * @param fileKey 文件键
     * @param destinationFile 目标文件
     * @param onProgress 进度回调 (已下载, 总大小, 百分比)
     * @return true 成功，false 失败
     */
    suspend fun downloadFile(
        fileKey: String,
        destinationFile: File,
        onProgress: ((Long, Long, Int) -> Unit)? = null
    ): Boolean {
        return try {
            Log.d(TAG, "开始下载，fileKey: $fileKey")

            val response = updateService.downloadFile(fileKey)

            if (!response.isSuccessful) {
                Log.e(TAG, "下载失败: HTTP ${response.code()}")
                return false
            }

            val body = response.body()
            if (body == null) {
                Log.e(TAG, "响应体为空")
                return false
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                destinationFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else 0

                        onProgress?.invoke(downloadedBytes, totalBytes, percent)
                    }
                }
            }

            Log.d(TAG, "下载完成: ${destinationFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "下载异常: ${e.message}", e)
            false
        }
    }

    /**
     * 验证文件完整性
     * @param file 要验证的文件
     * @param expectedMd5 期望的 MD5
     * @param expectedSha256 期望的 SHA256
     * @return true 验证通过，false 验证失败
     */
    fun verifyFile(file: File, expectedMd5: String, expectedSha256: String): Boolean {
        return try {
            Log.d(TAG, "验证文件: ${file.name}")

            val md5 = MessageDigest.getInstance("MD5")
            val sha256 = MessageDigest.getInstance("SHA-256")

            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    md5.update(buffer, 0, read)
                    sha256.update(buffer, 0, read)
                }
            }

            val actualMd5 = md5.digest().joinToString("") { "%02x".format(it) }
            val actualSha256 = sha256.digest().joinToString("") { "%02x".format(it) }

            val result = actualMd5.equals(expectedMd5, ignoreCase = true) &&
                    actualSha256.equals(expectedSha256, ignoreCase = true)

            Log.d(TAG, "文件验证${if (result) "成功" else "失败"}")
            result

        } catch (e: Exception) {
            Log.e(TAG, "验证异常: ${e.message}", e)
            false
        }
    }
}
