package com.miaomc.authLinker.service;

import com.miaomc.authLinker.AuthLinker;
import com.miaomc.authLinker.database.AuthRecordManager;
import com.miaomc.authLinker.utils.Base64Obfuscator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class AuthLinkGenerator {
    private final AuthLinker plugin;
    private final AuthRecordManager authRecordManager;
    private final Base64Obfuscator base64Obfuscator;
    private final String salt;
    private final int tokenLength;
    private final String endpoint;
    private final int cooldownTime;
    private final int expiredTime; // 添加过期时间字段

    /**
     * 构造函数
     *
     * @param plugin            插件实例
     * @param authRecordManager 认证记录管理器
     */
    public AuthLinkGenerator(AuthLinker plugin, AuthRecordManager authRecordManager) {
        this(plugin, authRecordManager, null);
    }

    /**
     * 构造函数（包含Base64混淆器）
     *
     * @param plugin            插件实例
     * @param authRecordManager 认证记录管理器
     * @param base64Obfuscator  Base64混淆器，可以为null
     */
    public AuthLinkGenerator(AuthLinker plugin, AuthRecordManager authRecordManager, Base64Obfuscator base64Obfuscator) {
        this.plugin = plugin;
        this.authRecordManager = authRecordManager;
        this.base64Obfuscator = base64Obfuscator;
        FileConfiguration config = plugin.getConfig();
        this.salt = config.getString("settings.salt", "abc123");
        this.tokenLength = config.getInt("settings.token_length", 12);
        this.endpoint = config.getString("settings.endpoint", "https://example.com/verify?data={data}&token={token}");
        this.cooldownTime = config.getInt("settings.cooldown", 120);
        this.expiredTime = config.getInt("settings.expired_time", 300); // 读取过期时间配置，默认5分钟
    }

    /**
     * 为玩家生成一个验证链接
     *
     * @param player 玩家对象
     * @param action 操作类型
     * @return 包含生成链接、数据和哈希的CompletableFuture
     */
    public CompletableFuture<AuthLinkResult> generateAuthLink(Player player, String action) {
        UUID playerUUID = player.getUniqueId();

        // 先检查冷却时间
        return authRecordManager.isInCooldownAsync(playerUUID, action)
                .thenCompose(isInCooldown -> {
                    if (isInCooldown) {
                        // 如果在冷却中，返回错误信息
                        AuthLinkResult result = new AuthLinkResult();
                        result.setSuccess(false);
                        result.setErrorMessage("操作太频繁，请等待" + cooldownTime + "秒后再试");
                        return CompletableFuture.completedFuture(result);
                    }

                    // 先生成记录UUID
                    String recordUUID = UUID.randomUUID().toString();
                    // 不在冷却中，生成新链接
                    String token = generateToken();
                    String plainBase64 = encodeAction(action, recordUUID, playerUUID, false); // 未混淆的Base64编码
                    String encodedAction = encodeAction(action, recordUUID, playerUUID); // 传入玩家UUID
                    String hash = generateHash(plainBase64, token);

                    // 写入数据库
                    return authRecordManager.writeAuthRecordAsync(playerUUID, action, token, recordUUID)
                            .thenApply(success -> {
                                AuthLinkResult result = new AuthLinkResult();
                                result.setSuccess(success);

                                if (success) {
                                    result.setData(encodedAction);
                                    result.setToken(token);
                                    result.setHash(hash);
                                    result.setRecordUUID(recordUUID);

                                    // 替换链接中的所有变量，包括token
                                    String link = endpoint.replace("{data}", encodedAction)
                                            .replace("{hash}", hash)
                                            .replace("{token}", token);
                                    result.setLink(link);
                                } else {
                                    result.setErrorMessage("生成链接时出错: 数据库写入失败");
                                }

                                return result;
                            });
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "生成认证链接时出错", ex);
                    AuthLinkResult result = new AuthLinkResult();
                    result.setSuccess(false);
                    result.setErrorMessage("生成链接时出错: " + ex.getMessage());
                    return result;
                });
    }

    /**
     * 将操作转换为JSON，然后Base64编码并可选择是否混淆处理
     *
     * @param action     操作类型
     * @param recordUUID 记录的UUID
     * @param playerUUID 玩家UUID
     * @param obfuscate  是否混淆数据，默认为false
     * @return 处理后的字符串
     */
    private String encodeAction(String action, String recordUUID, UUID playerUUID, Boolean obfuscate) {
        long currentTimeMillis = System.currentTimeMillis();
        long expiresTime = currentTimeMillis + (expiredTime * 1000L); // 计算过期时间的时间戳

        // 按照指定顺序构建JSON数据：recordUUID, action, player_uuid, expires_time
        String actionData = "{" +
                "\"recordUUID\":\"" + recordUUID + "\"," +
                "\"action\":\"" + action + "\"," +
                "\"player_uuid\":\"" + playerUUID.toString() + "\"," +
                "\"expires_time\":" + expiresTime +
                "}";

        // 首先获取标准Base64编码（用于哈希计算）
        String standardBase64 = java.util.Base64.getEncoder().encodeToString(actionData.getBytes(StandardCharsets.UTF_8));

        // 根据参数决定是否进行混淆
        if (obfuscate != null && obfuscate && base64Obfuscator != null) {
            return base64Obfuscator.obfuscate(actionData);
        } else {
            return standardBase64;
        }
    }

    // 重载方法，默认不混淆
    private String encodeAction(String action, String recordUUID, UUID playerUUID) {
        return encodeAction(action, recordUUID, playerUUID, false);
    }

    /**
     * 生成随机token
     *
     * @return 随机token字符串
     */
    private String generateToken() {
        String charPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder token = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < tokenLength; i++) {
            int index = random.nextInt(charPool.length());
            token.append(charPool.charAt(index));
        }

        return token.toString();
    }

    /**
     * 使用base64 + token + salt生成哈希值
     *
     * @param encodedData Base64编码后的数据
     * @param token       随机生成的token
     * @return SHA-256哈希值的十六进制字符串
     */
    private String generateHash(String encodedData, String token) {
        try {
            String input = encodedData + token + salt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            plugin.getLogger().log(Level.SEVERE, "哈希生成失败", e);
            return "";
        }
    }

    /**
     * 认证链接结果类
     */
    public static class AuthLinkResult {
        private boolean success;
        private String errorMessage;
        private String data;
        private String token;
        private String hash;
        private String link;
        private String recordUUID;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }

        public String getRecordUUID() {
            return recordUUID;
        }

        public void setRecordUUID(String recordUUID) {
            this.recordUUID = recordUUID;
        }
    }
}

