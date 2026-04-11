package fansirsqi.xposed.sesame.hook.network

import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.util.Files
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 抓包测试数据生成器 - 已适配不可变模型
 */
object CaptureTestData {

    fun injectDummyData() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dir = File(Files.MAIN_DIR.absolutePath + "/capture/" + today)
        if (!dir.exists()) dir.mkdirs()

        // 1. JSON 响应测试
        val id1 = UUID.randomUUID().toString()
        val body1 = "{\"success\":true,\"data\":{\"nickName\":\"蚂蚁金服\",\"userId\":\"208812345678\"}}"
        val bodyFile1 = File(dir, "${id1.substring(0, 8)}_res.bin")
        bodyFile1.writeText(body1)

        val p1 = CapturePacket(
            id = id1,
            url = "https://mobilegw.alipay.com/mgw.htm?method=alipay.user.info",
            method = "POST",
            host = "mobilegw.alipay.com",
            responseCode = 200,
            duration = 150,
            startTime = System.currentTimeMillis() - 5000,
            requestHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded", "User-Agent" to "Alipay"),
            responseHeaders = mapOf("Content-Type" to "application/json;charset=UTF-8", "Server" to "Tengine"),
            responseBodyFile = bodyFile1.absolutePath
        )
        savePacket(p1)

        // 2. 报错请求测试
        val p2 = CapturePacket(
            url = "https://api.m.taobao.com/rest/api3.do",
            method = "GET",
            host = "api.m.taobao.com",
            responseCode = 404,
            duration = 45,
            startTime = System.currentTimeMillis() - 2000
        )
        savePacket(p2)

        // 3. 图片请求测试
        val id3 = UUID.randomUUID().toString()
        val dummyImgFile = File(dir, "${id3.substring(0, 8)}_res.bin")
        dummyImgFile.writeBytes(byteArrayOf(0x89.toByte(), 'P'.toByte(), 'N'.toByte(), 'G'.toByte())) // PNG Header
        
        val p3 = CapturePacket(
            id = id3,
            url = "https://gw.alipayobjects.com/os/rmsportal/KDpgvguMpGfqaHPjicRK.png",
            method = "GET",
            host = "gw.alipayobjects.com",
            responseCode = 200,
            duration = 320,
            startTime = System.currentTimeMillis() - 1000,
            isImage = true,
            responseBodyFile = dummyImgFile.absolutePath,
            contentType = "image/png"
        )
        savePacket(p3)
    }

    private fun savePacket(packet: CapturePacket) {
        // 使用 CaptureFileManager 统一保存逻辑，自动处理元数据和 Body
        CaptureFileManager.save(packet, null, null)
    }
}
