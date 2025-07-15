package com.miaomc.authLinker.database;

import com.miaomc.authLinker.AuthLinker;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseInitializer {
    private final AuthLinker plugin;
    private final DatabaseManager databaseManager;
    private final String tableName;

    public DatabaseInitializer(AuthLinker plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        FileConfiguration config = plugin.getConfig();
        this.tableName = config.getString("database.table_name", "auth_records");
    }

    public void initializeDatabase() {
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        // 使用MySQL特定的语法优化表结构
        String createTableSQL = "CREATE TABLE IF NOT EXISTS `" + tableName + "` (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "action VARCHAR(255) NOT NULL," +
                "token VARCHAR(50) NOT NULL," +
                "status VARCHAR(50) NOT NULL DEFAULT 'unused'," +
                "is_used BOOLEAN DEFAULT FALSE," +
                "create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "update_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "expires_at TIMESTAMP NOT NULL," +
                "INDEX idx_player_uuid (player_uuid)," +
                "INDEX idx_token (token)," +
                "INDEX idx_expires_at (expires_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(createTableSQL)) {

            preparedStatement.executeUpdate();
            plugin.getLogger().info("MySQL数据表初始化成功：" + tableName);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL数据表初始化失败", e);
            throw new RuntimeException("无法初始化数据库表", e);
        }
    }

    /**
     * 获取表名
     */
    public String getTableName() {
        return tableName;
    }
}
