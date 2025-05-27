package com.miaomc.authLinker.database;

import com.miaomc.authLinker.AuthLinker;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class AuthRecordManager {
    private final AuthLinker plugin;
    private final DatabaseManager databaseManager;
    private final String tableName;
    private final int expiredTime;
    private final int cooldownTime;

    public AuthRecordManager(AuthLinker plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        FileConfiguration config = plugin.getConfig();
        this.tableName = config.getString("database.tableName");
        this.expiredTime = config.getInt("settings.expired_time");
        this.cooldownTime = config.getInt("settings.cooldown");
    }

    /**
     * 异步写入认证记录
     *
     * @param playerUUID 玩家UUID
     * @param action     操作类型
     * @param token      令牌
     * @return CompletableFuture 包含操作是否成功
     */
    public CompletableFuture<Boolean> writeAuthRecordAsync(UUID playerUUID, String action, String token) {
        // 生成一个新的UUID
        String recordUUID = UUID.randomUUID().toString();
        // 调用接受预设UUID的方法
        return writeAuthRecordAsync(playerUUID, action, token, recordUUID);
    }

    /**
     * 异步写入认证记录（使用预设UUID）
     *
     * @param playerUUID 玩家UUID
     * @param action     操作类型
     * @param token      令牌
     * @param recordUUID 预设的记录UUID
     * @return CompletableFuture 表示操作是否成功
     */
    public CompletableFuture<Boolean> writeAuthRecordAsync(UUID playerUUID, String action, String token, String recordUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 先检查是否有未过期的相同操作记录
                String activeLinkUUID = checkActiveLink(playerUUID, action);
                if (activeLinkUUID != null) {
                    // 如果存在有效记录，将其标记为已覆盖
                    markRecordAsCovered(activeLinkUUID);
                }

                // 写入新记录
                writeAuthRecord(recordUUID, playerUUID.toString(), action, token);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "写入认证记录失败", e);
                return false;
            }
        });
    }

    /**
     * 检查玩家是否有活跃的认证链接并在冷却中
     *
     * @param playerUUID 玩家UUID
     * @param action     操作类型
     * @return 如果在冷却中返回true，否则返回false
     */
    public CompletableFuture<Boolean> isInCooldownAsync(UUID playerUUID, String action) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection()) {
                long cooldownThreshold = Instant.now().getEpochSecond() - cooldownTime;
                String sql = "SELECT uuid FROM " + tableName +
                        " WHERE player_uuid = ? AND action = ? AND " +
                        "UNIX_TIMESTAMP(create_at) > ? AND " +
                        "(is_used = FALSE AND status != 'covered')";

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, playerUUID.toString());
                    statement.setString(2, action);
                    statement.setLong(3, cooldownThreshold);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next(); // 如果有结果则在冷却中
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "检查冷却时间失败", e);
                return false;
            }
        });
    }

    /**
     * 获取玩家有效的认证记录UUID
     *
     * @param playerUUID 玩家UUID
     * @param action     操作类型
     * @return 有效记录的UUID，如果不存在则返回null
     */
    private String checkActiveLink(UUID playerUUID, String action) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            long currentTime = Instant.now().getEpochSecond();
            String sql = "SELECT uuid FROM " + tableName +
                    " WHERE player_uuid = ? AND action = ? AND " +
                    "UNIX_TIMESTAMP(expires_at) > ? AND " +
                    "is_used = FALSE";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, action);
                statement.setLong(3, currentTime);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString("uuid");
                    }
                    return null;
                }
            }
        }
    }

    /**
     * 将记录标记为已覆盖
     *
     * @param uuid 记录UUID
     * @throws SQLException 数据库异常
     */
    private void markRecordAsCovered(String uuid) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            String sql = "UPDATE " + tableName +
                    " SET is_used = TRUE, status = 'covered', expires_at = CURRENT_TIMESTAMP " +
                    "WHERE uuid = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.executeUpdate();
            }
        }
    }

    /**
     * 写入新的认证记录
     *
     * @param uuid       记录UUID
     * @param playerUUID 玩家UUID
     * @param action     操作类型
     * @param token      令牌
     * @throws SQLException 数据库异常
     */
    private void writeAuthRecord(String uuid, String playerUUID, String action, String token) throws SQLException {
        try (Connection connection = databaseManager.getConnection()) {
            String sql = "INSERT INTO " + tableName +
                    " (uuid, player_uuid, action, token, expires_at) VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? SECOND))";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.setString(2, playerUUID);
                statement.setString(3, action);
                statement.setString(4, token);

                // 使用数据库函数直接计算过期时间，避免时区问题
                statement.setInt(5, expiredTime);

                statement.executeUpdate();
            }
        }
    }

    /**
     * 将记录标记为已使用
     *
     * @param uuid 记录UUID
     * @param status 状态描述
     */
    public CompletableFuture<Boolean> markRecordAsUsedAsync(String uuid, String status) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection()) {
                String sql = "UPDATE " + tableName + " SET is_used = TRUE, status = ? WHERE uuid = ?";

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, status);
                    statement.setString(2, uuid);
                    int rowsAffected = statement.executeUpdate();
                    return rowsAffected > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "标记记录为已使用失败", e);
                return false;
            }
        });
    }
}
