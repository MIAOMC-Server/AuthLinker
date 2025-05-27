package com.miaomc.authLinker.utils;

import org.bukkit.configuration.file.FileConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Base64混淆工具类，用于对Base64编码的字符串进行混淆处理
 * 包括位移和字符替换两种混淆方式
 */
public class Base64Obfuscator {
    // 标准Base64字符集
    private static final String STANDARD_BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    // 位移位数
    private final int shift;

    // 混淆表
    private final String obfuscationTable;

    // 旋转时间戳（秒）
    private final int rotationTimestamp;

    // 字符映射表
    private final Map<Character, Character> obfuscationMap;
    private final Map<Character, Character> deobfuscationMap;

    /**
     * 构造函数，初始化混淆器
     *
     * @param config 插件配置
     */
    public Base64Obfuscator(FileConfiguration config) {
        this.shift = config.getInt("settings.base64_shift", 3);
        this.obfuscationTable = config.getString("settings.base64_obfuscation_table",
                "jQNHxo9a1zVG8dFcyb27XmiwOl0WULnkPsBKqEAZYfer3t5RMDSCJhgvu4pT-_");
        this.rotationTimestamp = config.getInt("settings.rotation_timestamp", 86400);

        // 初始化混淆映射表
        this.obfuscationMap = new HashMap<>();
        this.deobfuscationMap = new HashMap<>();

        // 初始化映射表
        initMappingTables(this.obfuscationTable);
    }
    
    /**
     * 初始化混淆映射表和解混淆映射表
     * 
     * @param customTable 要使用的混淆表
     */
    private void initMappingTables(String customTable) {
        // 清空现有映射
        obfuscationMap.clear();
        deobfuscationMap.clear();
        
        // 建立标准Base64字符和混淆字符之间的映射关系
        for (int i = 0; i < STANDARD_BASE64_CHARS.length(); i++) {
            char standardChar = STANDARD_BASE64_CHARS.charAt(i);
            char obfuscatedChar = i < customTable.length() ? customTable.charAt(i) : standardChar;

            obfuscationMap.put(standardChar, obfuscatedChar);
            deobfuscationMap.put(obfuscatedChar, standardChar);
        }

        // 特殊处理等号
        obfuscationMap.put('=', '_');
        deobfuscationMap.put('_', '=');
    }

    /**
     * 对字符串进行Base64编码并混淆，使用当前时间戳
     *
     * @param input 原始字符串
     * @return 混淆后的Base64字符串和时间戳的JSON的Base64编码
     */
    public String obfuscate(String input) {
        return obfuscate(input, System.currentTimeMillis());
    }

    /**
     * 对字符串进行Base64编码并混淆，使用指定时间戳
     *
     * @param input 原始字符串
     * @param timestamp 用于混淆的时间戳
     * @return 混淆后的Base64字符串和时间戳的JSON的Base64编码
     */
    public String obfuscate(String input, long timestamp) {
        // 使用时间戳更新混淆表
        String rotatedTable = offsetBase64Table(timestamp);
        initMappingTables(rotatedTable);

        // 标准Base64编码
        String base64Encoded = Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));

        // 应用位移
        String shifted = applyShift(base64Encoded, shift);

        // 应用字符替换
        StringBuilder obfuscated = new StringBuilder();
        for (char c : shifted.toCharArray()) {
            obfuscated.append(obfuscationMap.getOrDefault(c, c));
        }

        // 创建包含混淆数据和时间戳的JSON
        String json = String.format("{\"data\":\"%s\",\"time\":%d}", obfuscated, timestamp);
        
        // 对JSON进行Base64编码
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码混淆的Base64字符串
     *
     * @param input 混淆后的Base64字符串和时间戳的JSON的Base64编码
     * @return 解码后的原始字符串
     */
    public String deobfuscate(String input) {
        try {
            // 解码Base64得到JSON
            String json = new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
            
            // 简单解析JSON以提取数据和时间戳
            // 格式: {"data":"混淆数据","time":时间戳}
            String data = json.substring(json.indexOf("\"data\":\"") + 8, json.indexOf("\",\"time\""));
            String timeStr = json.substring(json.indexOf("\"time\":") + 7, json.indexOf("}"));
            long timestamp = Long.parseLong(timeStr);
            
            // 使用相同的时间戳重新生成混淆表
            String rotatedTable = offsetBase64Table(timestamp);
            initMappingTables(rotatedTable);
            
            // 应用字符替换反向操作
            StringBuilder deobfuscated = new StringBuilder();
            for (char c : data.toCharArray()) {
                deobfuscated.append(deobfuscationMap.getOrDefault(c, c));
            }
            
            // 应用位移反向操作
            String shifted = applyShift(deobfuscated.toString(), -shift);
            
            // Base64解码
            return new String(Base64.getDecoder().decode(shifted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 如果解析失败，尝试使用旧格式解码
            return deobfuscateOldFormat(input);
        }
    }
    
    /**
     * 解码旧格式的混淆Base64字符串（向后兼容）
     *
     * @param input 混淆后的Base64字符串
     * @return 解码后的原始字符串
     */
    private String deobfuscateOldFormat(String input) {
        // 应用字符替换反向操作
        StringBuilder deobfuscated = new StringBuilder();
        for (char c : input.toCharArray()) {
            deobfuscated.append(deobfuscationMap.getOrDefault(c, c));
        }

        // 应用位移反向操作
        String shifted = applyShift(deobfuscated.toString(), -shift);

        // Base64解码
        return new String(Base64.getDecoder().decode(shifted), StandardCharsets.UTF_8);
    }

    /**
     * 应用凯撒位移到Base64字符串
     *
     * @param input       输入Base64字符串
     * @param shiftAmount 位移量（正数为向右，负数为向左）
     * @return 位移后的字符串
     */
    private String applyShift(String input, int shiftAmount) {
        StringBuilder result = new StringBuilder();

        for (char c : input.toCharArray()) {
            if (c == '=' || c == '_') {
                // 保留等号不做位移
                result.append(c);
                continue;
            }

            int index = STANDARD_BASE64_CHARS.indexOf(c);
            if (index != -1) {
                // 对于Base64字符进行位移
                int newIndex = (index + shiftAmount) % STANDARD_BASE64_CHARS.length();
                if (newIndex < 0) {
                    newIndex += STANDARD_BASE64_CHARS.length();
                }
                result.append(STANDARD_BASE64_CHARS.charAt(newIndex));
            } else {
                // 对于非Base64字符不做位移
                result.append(c);
            }
        }

        return result.toString();
    }
    
    /**
     * 根据配置的位移和混淆表对Base64字符集进行偏移处理
     *
     * @return 偏移后的Base64字符集
     */
    public String offsetBase64Table() {
        return offsetBase64Table(System.currentTimeMillis());
    }
    
    /**
     * 根据配置的位移和混淆表对Base64字符集进行偏移处理，使用指定的时间戳
     *
     * @param timestamp 用于计算旋转的时间戳
     * @return 偏移后的Base64字符集
     */
    public String offsetBase64Table(long timestamp) {
        long seed = timestamp / (rotationTimestamp * 1000L); // 计算经过的旋转周期数（天数）
        char[] tableArr = obfuscationTable.toCharArray();
        SeededRandom random = new SeededRandom(seed);

        // Fisher-Yates 洗牌
        for (int i = tableArr.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = tableArr[i];
            tableArr[i] = tableArr[j];
            tableArr[j] = temp;
        }
        return new String(tableArr);
    }
}
