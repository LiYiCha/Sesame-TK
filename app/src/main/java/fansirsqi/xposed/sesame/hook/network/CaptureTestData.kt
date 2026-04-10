package fansirsqi.xposed.sesame.hook.network

import fansirsqi.xposed.sesame.hook.network.model.CapturePacket
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.JsonUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 抓包测试数据生成器
 */
object CaptureTestData {

    fun injectDummyData() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dir = File(Files.MAIN_DIR.absolutePath + "/capture/" + today)
        if (!dir.exists()) dir.mkdirs()

        // 1. JSON 响应测试
        val p1 = CapturePacket(
            url = "https://mobilegw.alipay.com/mgw.htm?method=alipay.user.info",
            method = "POST",
            host = "mobilegw.alipay.com",
            responseCode = 200,
            duration = 150,
            startTime = System.currentTimeMillis() - 5000
        )
        p1.requestHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded", "User-Agent" to "Alipay")
        p1.responseHeaders = mapOf("Content-Type" to "application/json;charset=UTF-8", "Server" to "Tengine")
        
        val body1 = "{\"success\":true,\"data\":{\"nickName\":\"蚂蚁金服\",\"userId\":\"208812345678\"}}"
        val bodyFile1 = File(dir, "${p1.id}_res.bin")
        bodyFile1.writeText(body1)
        p1.responseBodyFile = bodyFile1.absolutePath
        
        savePacket(dir, p1)

        // 2. 报错请求测试
        val p2 = CapturePacket(
            url = "https://api.m.taobao.com/rest/api3.do",
            method = "GET",
            host = "api.m.taobao.com",
            responseCode = 404,
            duration = 45,
            startTime = System.currentTimeMillis() - 2000
        )
        savePacket(dir, p2)

        // 3. 图片请求测试 (使用简单的占位数据)
        val p3 = CapturePacket(
            url = "https://gw.alipayobjects.com/os/rmsportal/KDpgvguMpGfqaHPjicRK.png",
            method = "GET",
            host = "gw.alipayobjects.com",
            responseCode = 200,
            duration = 320,
            startTime = System.currentTimeMillis() - 1000,
            isImage = true
        )
        // 注意：这里没有真实的图片数据，仅作为 UI 路径占位测试
        val dummyImgFile = File(dir, "${p3.id}_res.bin")
        dummyImgFile.writeBytes(byteArrayOf(0x89.toByte(), 'P'.toByte(), 'N'.toByte(), 'G'.toByte())) // PNG Header
        p3.responseBodyFile = dummyImgFile.absolutePath
        
        savePacket(dir, p3)
    }

    private fun savePacket(dir: File, packet: CapturePacket) {
        // 使用 CaptureFileManager 统一保存逻辑，自动处理 JSON 存储和格式
        CaptureFileManager.save(packet, null, null)
    }
}
