package com.miaomc.authLinker.database;

import com.miaomc.authLinker.AuthLinker;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class AuthRecordManager {
    private final AuthLinker plugin;
    private final DatabaseManager databaseManager;
    private final DatabaseInitializer databaseInitializer;
    private final int expiredTime;
    private final int cooldownTime;

    public AuthRecordManager(AuthLinker plugin, DatabaseManager databaseManager, DatabaseInitializer databaseInitializer) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.databaseInitializer = databaseInitializer;
        FileConfiguration config = plugin.getConfig();
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
            String sql = "INSERT INTO `" + databaseInitializer.getTableName() +
                        "` (uuid, player_uuid, action, token, expires_at) VALUES (?, ?, ?, ?, ?)";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

                // 计算过期时间
                Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + (expiredTime * 1000L));

                preparedStatement.setString(1, recordUUID);
                preparedStatement.setString(2, playerUUID.toString());
                preparedStatement.setString(3, action);
                preparedStatement.setString(4, token);
                preparedStatement.setTimestamp(5, expiresAt);

                int rowsAffected = preparedStatement.executeUpdate();
                return rowsAffected > 0;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "写入认证记录失败", e);
                return false;
            }
        });
    }

    /**
     * 异步检查玩家是否在冷却期内
     *
     * @param playerUUID 玩家UUID
     * @param action     操作类型
     * @return CompletableFuture 包含是否在冷却期内
     */
    public CompletableFuture<Boolean> isInCooldownAsync(UUID playerUUID, String action) {
        return CompletableFuture.supplyAsync(() -> {
            // 修复冷却时间逻辑：查询最近一条记录的创建时间
            String sql = "SELECT create_at FROM `" + databaseInitializer.getTableName() +
                        "` WHERE player_uuid = ? AND action = ? ORDER BY create_at DESC LIMIT 1";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

                preparedStatement.setString(1, playerUUID.toString());
                preparedStatement.setString(2, action);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        Timestamp lastCreateTime = resultSet.getTimestamp("create_at");
                        long currentTime = System.currentTimeMillis();
                        long lastCreateTimeMillis = lastCreateTime.getTime();
                        long timeDifference = currentTime - lastCreateTimeMillis;

                        // 如果时间差小于冷却时间（毫秒），则还在冷却期内
                        return timeDifference < (cooldownTime * 1000L);
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "检查冷却时间失败", e);
            }

            // 如果没有找到记录，说明没有冷却限制
            return false;
        });
    }

    /**
     * 异步标记记录为已使用
     *
     * @param uuid 记录UUID
     * @return CompletableFuture 包含操作是否成功
     */
    public CompletableFuture<Boolean> markAsUsedAsync(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE `" + databaseInitializer.getTableName() +
                        "` SET is_used = TRUE, status = 'used', update_at = CURRENT_TIMESTAMP WHERE uuid = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

                preparedStatement.setString(1, uuid);
                int rowsAffected = preparedStatement.executeUpdate();
                return rowsAffected > 0;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "标记记录为已使用失败", e);
                return false;
            }
        });
    }

    /**
     * 异步清理过期记录
     */
    public CompletableFuture<Integer> cleanupExpiredRecordsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM `" + databaseInitializer.getTableName() + "` WHERE expires_at < CURRENT_TIMESTAMP";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

                int deletedRows = preparedStatement.executeUpdate();
                if (deletedRows > 0) {
                    plugin.getLogger().info("清理了 " + deletedRows + " 条过期记录");
                }
                return deletedRows;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "清理过期记录失败", e);
                return 0;
            }
        });
    }

    /**
     * 异步验证记录是否有效
     *
     * @param uuid  记录UUID
     * @param token 令牌
     * @return CompletableFuture 包含记录是否有效
     */
    public CompletableFuture<Boolean> isRecordValidAsync(String uuid, String token) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM `" + databaseInitializer.getTableName() +
                        "` WHERE uuid = ? AND token = ? AND is_used = FALSE AND expires_at > CURRENT_TIMESTAMP";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

                preparedStatement.setString(1, uuid);
                preparedStatement.setString(2, token);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt(1) > 0;
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "验证记录失败", e);
            }

            return false;
        });
    }
}
