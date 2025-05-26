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

        // 初始化混淆映射表
        this.obfuscationMap = new HashMap<>();
        this.deobfuscationMap = new HashMap<>();

        // 建立标准Base64字符和混淆字符之间的映射关系
        for (int i = 0; i < STANDARD_BASE64_CHARS.length(); i++) {
            char standardChar = STANDARD_BASE64_CHARS.charAt(i);
            char obfuscatedChar = i < obfuscationTable.length() ? obfuscationTable.charAt(i) : standardChar;

            obfuscationMap.put(standardChar, obfuscatedChar);
            deobfuscationMap.put(obfuscatedChar, standardChar);
        }

        // 特殊处理等号
        obfuscationMap.put('=', '_');
        deobfuscationMap.put('_', '=');
    }

    /**
     * 对字符串进行Base64编码并混淆
     *
     * @param input 原始字符串
     * @return 混淆后的Base64字符串
     */
    public String obfuscate(String input) {
        // 标准Base64编码
        String base64Encoded = Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));

        // 应用位移
        String shifted = applyShift(base64Encoded, shift);

        // 应用字符替换
        StringBuilder obfuscated = new StringBuilder();
        for (char c : shifted.toCharArray()) {
            obfuscated.append(obfuscationMap.getOrDefault(c, c));
        }

        return obfuscated.toString();
    }

    /**
     * 解码混淆的Base64字符串
     *
     * @param input 混淆后的Base64字符串
     * @return 解码后的原始字符串
     */
    public String deobfuscate(String input) {
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
}
