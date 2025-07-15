package com.miaomc.authLinker.service;

import com.miaomc.authLinker.AuthLinker;
import com.miaomc.authLinker.database.AuthRecordManager;
import com.miaomc.authLinker.utils.RSAEncryptor;
import com.miaomc.authLinker.utils.CooldownManager;
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
    private final RSAEncryptor rsaEncryptor;
    private final CooldownManager cooldownManager;
    private final String salt;
    private final int tokenLength;
    private final String endpoint;
    private final int expiredTime;

    /**
     * 构造函数
     *
     * @param plugin            插件实例
     * @param authRecordManager 认证记录管理器
     * @param rsaEncryptor      RSA加密器
     * @param cooldownManager   冷却时间管理器
     */
    public AuthLinkGenerator(AuthLinker plugin, AuthRecordManager authRecordManager, RSAEncryptor rsaEncryptor, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.authRecordManager = authRecordManager;
        this.rsaEncryptor = rsaEncryptor;
        this.cooldownManager = cooldownManager;
        FileConfiguration config = plugin.getConfig();
        this.salt = config.getString("settings.salt", "abc123");
        this.tokenLength = config.getInt("settings.token_length", 12);
        this.endpoint = config.getString("settings.endpoint", "https://example.com/verify?data={data}&hash={hash}");
        this.expiredTime = config.getInt("settings.expired_time", 300);
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

        // 检查RSA密钥是否已加载
        if (!rsaEncryptor.isKeysLoaded()) {
            AuthLinkResult result = new AuthLinkResult();
            result.setSuccess(false);
            result.setErrorMessage(plugin.getConfig().getString("messages.error.keys_not_loaded", "RSA密钥未加载，请先生成密钥对"));
            return CompletableFuture.completedFuture(result);
        }

        // 使用内存缓存检查冷却时间（同步操作，更快速）
        if (cooldownManager.isInCooldown(playerUUID, action)) {
            AuthLinkResult result = new AuthLinkResult();
            result.setSuccess(false);

            // 获取剩余冷却时间并显示给玩家
            int remainingSeconds = cooldownManager.getRemainingCooldown(playerUUID, action);
            String cooldownMsg = plugin.getConfig().getString("messages.error.cooldown", "操作太频繁，请等待 {cooldown} 秒后再试");
            result.setErrorMessage(cooldownMsg.replace("{cooldown}", String.valueOf(remainingSeconds)));
            return CompletableFuture.completedFuture(result);
        }

        // 不在冷却中，生成新链接
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 先生成记录UUID
                String recordUUID = UUID.randomUUID().toString();
                String token = generateToken();
                String plainBase64 = encodeActionForHash(action, recordUUID, playerUUID); // 用于哈希计算的Base64编码
                String encryptedData = encodeActionWithRSA(action, recordUUID, playerUUID); // RSA加密的数据
                String hash = generateHash(plainBase64, token);

                AuthLinkResult result = new AuthLinkResult();

                // 写入数据库
                boolean success = authRecordManager.writeAuthRecordAsync(playerUUID, action, token, recordUUID).join();

                if (success) {
                    // 数据库写入成功后，记录冷却时间
                    cooldownManager.recordAction(playerUUID, action);

                    result.setSuccess(true);
                    result.setData(encryptedData);
                    result.setToken(token);
                    result.setHash(hash);
                    result.setRecordUUID(recordUUID);

                    // 替换链接中的变量（不包含token，token在服务器端查询）
                    String link = endpoint.replace("{data}", encryptedData)
                            .replace("{hash}", hash);
                    result.setLink(link);
                } else {
                    result.setSuccess(false);
                    String dbErrorMsg = plugin.getConfig().getString("messages.error.database_error", "生成链接时出错: 数据库写入失败");
                    result.setErrorMessage(dbErrorMsg);
                }

                return result;
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "生成认证链接时出错", ex);
                AuthLinkResult result = new AuthLinkResult();
                result.setSuccess(false);
                String generalErrorMsg = plugin.getConfig().getString("messages.error.general_error", "生成链接时出错: {error}");
                result.setErrorMessage(generalErrorMsg.replace("{error}", ex.getMessage()));
                return result;
            }
        });
    }

    /**
     * 将操作转换为JSON，然后使用RSA加密
     *
     * @param action     操作类型
     * @param recordUUID 记录的UUID
     * @param playerUUID 玩家UUID
     * @return RSA加密后的字符串
     */
    private String encodeActionWithRSA(String action, String recordUUID, UUID playerUUID) {
        long currentTimeMillis = System.currentTimeMillis();
        long expiresTime = currentTimeMillis + (expiredTime * 1000L);

        // 按照指定顺序构建JSON数据：uuid, action, player_uuid, expires_time
        String actionData = "{" +
                "\"uuid\":\"" + recordUUID + "\"," +
                "\"action\":\"" + action + "\"," +
                "\"player_uuid\":\"" + playerUUID.toString() + "\"," +
                "\"expires_time\":" + expiresTime +
                "}";

        // 使用RSA加密数据
        return rsaEncryptor.encrypt(actionData);
    }

    /**
     * 将操作转换为JSON，然后Base64编码（仅用于哈希计算，保持原有哈希逻辑不变）
     *
     * @param action     操作类型
     * @param recordUUID 记录的UUID
     * @param playerUUID 玩家UUID
     * @return Base64编码的字符串
     */
    private String encodeActionForHash(String action, String recordUUID, UUID playerUUID) {
        long currentTimeMillis = System.currentTimeMillis();
        long expiresTime = currentTimeMillis + (expiredTime * 1000L);

        // 按照指定顺序构建JSON数据：uuid, action, player_uuid, expires_time
        String actionData = "{" +
                "\"uuid\":\"" + recordUUID + "\"," +
                "\"action\":\"" + action + "\"," +
                "\"player_uuid\":\"" + playerUUID.toString() + "\"," +
                "\"expires_time\":" + expiresTime +
                "}";

        // 返回标准Base64编码（用于哈希计算，保持原有逻辑）
        return java.util.Base64.getEncoder().encodeToString(actionData.getBytes(StandardCharsets.UTF_8));
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
     * 使用base64 + token + salt生成哈希值（保持原有哈希逻辑不变）
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
        private String link;
        private String data;
        private String token;
        private String hash;
        private String recordUUID;
        private String errorMessage;

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
        public String getRecordUUID() { return recordUUID; }
        public void setRecordUUID(String recordUUID) { this.recordUUID = recordUUID; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
