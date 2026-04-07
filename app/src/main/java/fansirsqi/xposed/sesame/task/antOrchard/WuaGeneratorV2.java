package fansirsqi.xposed.sesame.task.antOrchard;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * WUA生成器 V2 - 基于实际测试的可用版本
 * 
 * 重要发现:
 * 1. 真实WUA是高度随机/加密的数据（熵值7.37+）
 * 2. 但是简单的随机数据也能通过验证（模拟WUA测试成功）
 * 3. 前3字节可能是版本标识: 0xAB 0xC4 0x35
 * 4. 总长度必须是323字节
 * 
 * 策略: 生成高熵随机数据，保留关键标识字节
 */
public class WuaGeneratorV2 {
    
    // 魔数/版本标识（从真实WUA中提取）
    private static final byte[] MAGIC_BYTES = {
        (byte)0xAB, (byte)0xC4, (byte)0x35
    };
    
    private static final int TOTAL_LENGTH = 323;
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * 生成WUA字符串
     * 策略: 高熵随机数据 + 关键标识字节
     * 
     * @return Base64编码的WUA字符串
     */
    public static String generate() {
        byte[] wuaBytes = new byte[TOTAL_LENGTH];
        
        // 填充随机数据
        random.nextBytes(wuaBytes);
        
        // 设置魔数（前3字节）
        System.arraycopy(MAGIC_BYTES, 0, wuaBytes, 0, MAGIC_BYTES.length);
        
        // 可选: 在特定位置插入时间戳（如果需要）
         int timestamp = (int) (System.currentTimeMillis() / 1000);
         ByteBuffer.wrap(wuaBytes, 8, 4).putInt(timestamp);
        
        // Base64编码
        return Base64.getEncoder().encodeToString(wuaBytes);
    }
    
    /**
     * 生成WUA的简化版本（用于测试）
     * 
     * @return Base64编码的WUA字符串
     */
    public static String generateSimple() {
        byte[] wuaBytes = new byte[TOTAL_LENGTH];
        random.nextBytes(wuaBytes);
        System.arraycopy(MAGIC_BYTES, 0, wuaBytes, 0, MAGIC_BYTES.length);
        return Base64.getEncoder().encodeToString(wuaBytes);
    }
    
    /**
     * 生成带时间戳的WUA
     * 在偏移8-11位置插入时间戳
     * 
     * @param timestampSeconds Unix时间戳(秒)
     * @return Base64编码的WUA字符串
     */
    public static String generateWithTimestamp(long timestampSeconds) {
        byte[] wuaBytes = new byte[TOTAL_LENGTH];
        random.nextBytes(wuaBytes);
        
        // 设置魔数
        System.arraycopy(MAGIC_BYTES, 0, wuaBytes, 0, MAGIC_BYTES.length);
        
        // 设置时间戳（偏移8-11）
        ByteBuffer.wrap(wuaBytes, 8, 4).putInt((int) timestampSeconds);
        
        return Base64.getEncoder().encodeToString(wuaBytes);
    }
    
    /**
     * 验证WUA格式
     * 
     * @param wuaString Base64编码的WUA字符串
     * @return true表示格式正确
     */
    public static boolean validate(String wuaString) {
        try {
            byte[] decoded = Base64.getDecoder().decode(wuaString);
            
            // 检查长度
            if (decoded.length != TOTAL_LENGTH) {
                return false;
            }
            
            // 检查魔数（可选）
            for (int i = 0; i < MAGIC_BYTES.length; i++) {
                if (decoded[i] != MAGIC_BYTES[i]) {
                    System.out.println("警告: 魔数不匹配，但可能仍然有效");
                    break;
                }
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 测试方法
     */
    public static void main(String[] args) {
        System.out.println("WUA生成器 V2 - 基于实际测试");
        System.out.println("=" .repeat(70));
        
        // 生成5个WUA
        System.out.println("\n生成5个随机WUA:");
        for (int i = 0; i < 5; i++) {
            String wua = generate();
            System.out.println((i + 1) + ": " + wua);
            System.out.println("   长度: " + wua.length() + " 字符");
            System.out.println("   验证: " + (validate(wua) ? "✓ 通过" : "✗ 失败"));
        }
        
        // 生成带时间戳的WUA
        System.out.println("\n生成带时间戳的WUA:");
        long timestamp = System.currentTimeMillis() / 1000;
        String wuaWithTs = generateWithTimestamp(timestamp);
        System.out.println("时间戳: " + timestamp);
        System.out.println("WUA: " + wuaWithTs);
        System.out.println("验证: " + (validate(wuaWithTs) ? "✓ 通过" : "✗ 失败"));
        
        // 验证已知的可用WUA
        System.out.println("\n验证已知可用的模拟WUA:");
        String knownWorkingWua = "sS6/eMw8JHZOs4Z7wF/1EBaA10rsqnp+GNF1GiOczjcnhGji5u1o6HxSHus6S0Er7TCMHsKnqCl7NK7aZSRsfJncLw6gxWhB5UrvaAqaXV1lGS7hjDJE6I7291eGiRgGtfgyhKcqGyBe6dxzQ6ncJpx5AqrHQK+LzluHObXjcGKUyPUxWpxZueRiyDHTapeA3u8hgp/Q2QZB+e05Av9hvVDv81vZgRBCAX7uIFHt1FyTbm8u8xXXWxUYCdmyrq8I5PThKBpcvl/nbzAzQrAwlW8I/qNlk2OdlmNdKJDIQoUsF/TxdXmD7znqEK0U6etgGb1IrYV5DO++pSAilmIT1Dv3lYuZJgXht1vaKB9rB7OccCvNz9fYPxK5iMaeuM55M9xAAQp4MuQxQ/H0257xydL7aqouEkew3ZurgyOPLZfJg7Y=";
        System.out.println("验证: " + (validate(knownWorkingWua) ? "✓ 通过" : "✗ 失败"));
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("注意:");
        System.out.println("1. 生成的WUA是高熵随机数据");
        System.out.println("2. 保留了前3字节的魔数标识");
        System.out.println("3. 实际使用时建议先测试验证");
        System.out.println("4. 如果服务器验证失败，可能需要更多逆向分析");
    }
}
