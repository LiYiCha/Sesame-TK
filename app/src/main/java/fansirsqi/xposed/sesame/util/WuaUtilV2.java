package fansirsqi.xposed.sesame.util;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * WUA工具类 V2 - 用于生成支付宝WUA参数
 * 
 * 重要发现（基于实际测试）:
 * 1. 真实WUA是高度随机/加密的数据（熵值7.37+）
 * 2. 简单的随机数据也能通过验证（已测试）
 * 3. 前3字节是魔数标识: 0xAB 0xC4 0x35
 * 4. 总长度必须是323字节
 * 
 * @author 逆向分析
 * @version 2.0
 */
public class WuaUtilV2 {
    
    // 魔数/版本标识（从真实WUA中提取）
    private static final byte[] MAGIC_BYTES = {
        (byte)0xAB, (byte)0xC4, (byte)0x35
    };
    
    private static final int TOTAL_LENGTH = 323;
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * 生成WUA字符串（使用当前时间戳）
     * 策略: 高熵随机数据 + 魔数标识
     * 
     * @return Base64编码的WUA字符串
     */
    public static String generate() {
        return generateWithTimestamp(System.currentTimeMillis() / 1000);
    }
    
    /**
     * 生成带自定义时间戳的WUA
     * 
     * @param timestampSeconds Unix时间戳(秒)
     * @return Base64编码的WUA字符串
     */
    public static String generateWithTimestamp(long timestampSeconds) {
        byte[] wuaBytes = new byte[TOTAL_LENGTH];
        
        // 填充随机数据
        random.nextBytes(wuaBytes);
        
        // 设置魔数（前3字节）
        System.arraycopy(MAGIC_BYTES, 0, wuaBytes, 0, MAGIC_BYTES.length);
        
        // 设置时间戳（偏移8-11）- 可选
        ByteBuffer.wrap(wuaBytes, 8, 4).putInt((int) timestampSeconds);
        
        // Base64编码（兼容Android）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return java.util.Base64.getEncoder().encodeToString(wuaBytes);
        } else {
            return android.util.Base64.encodeToString(wuaBytes, android.util.Base64.NO_WRAP);
        }
    }
    
    /**
     * 生成纯随机WUA（不包含时间戳）
     * 
     * @return Base64编码的WUA字符串
     */
    public static String generateRandom() {
        byte[] wuaBytes = new byte[TOTAL_LENGTH];
        random.nextBytes(wuaBytes);
        System.arraycopy(MAGIC_BYTES, 0, wuaBytes, 0, MAGIC_BYTES.length);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return java.util.Base64.getEncoder().encodeToString(wuaBytes);
        } else {
            return android.util.Base64.encodeToString(wuaBytes, android.util.Base64.NO_WRAP);
        }
    }
    
    /**
     * 解析WUA中的时间戳
     * 
     * @param wuaString Base64编码的WUA字符串
     * @return Unix时间戳(秒)，解析失败返回-1
     */
    public static long parseTimestamp(String wuaString) {
        try {
            byte[] decoded;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                decoded = java.util.Base64.getDecoder().decode(wuaString);
            } else {
                decoded = android.util.Base64.decode(wuaString, android.util.Base64.NO_WRAP);
            }
            
            if (decoded.length >= 12) {
                ByteBuffer buffer = ByteBuffer.wrap(decoded, 8, 4);
                return buffer.getInt() & 0xFFFFFFFFL;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }
    
    /**
     * 验证WUA格式是否正确
     * 
     * @param wuaString Base64编码的WUA字符串
     * @return true表示格式正确
     */
    public static boolean validate(String wuaString) {
        try {
            byte[] decoded;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                decoded = java.util.Base64.getDecoder().decode(wuaString);
            } else {
                decoded = android.util.Base64.decode(wuaString, android.util.Base64.NO_WRAP);
            }
            
            // 检查长度
            if (decoded.length != TOTAL_LENGTH) {
                return false;
            }
            
            // 注意: 魔数检查是可选的，因为模拟WUA没有魔数也能用
            // 这里只检查长度
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查WUA是否包含正确的魔数
     * 
     * @param wuaString Base64编码的WUA字符串
     * @return true表示包含正确的魔数
     */
    public static boolean hasMagicBytes(String wuaString) {
        try {
            byte[] decoded;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                decoded = java.util.Base64.getDecoder().decode(wuaString);
            } else {
                decoded = android.util.Base64.decode(wuaString, android.util.Base64.NO_WRAP);
            }
            
            if (decoded.length < MAGIC_BYTES.length) {
                return false;
            }
            
            for (int i = 0; i < MAGIC_BYTES.length; i++) {
                if (decoded[i] != MAGIC_BYTES[i]) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
