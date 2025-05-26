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
        this.tableName = config.getString("database.tableName");
    }

    public void initializeDatabase() {
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "action VARCHAR(255) NOT NULL," +
                "token VARCHAR(50) NOT NULL," +
                "is_used VARCHAR(50)," +
                "create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "expires_at TIMESTAMP NOT NULL" +
                ")";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(createTableSQL)) {

            preparedStatement.executeUpdate();
            plugin.getLogger().info("数据表初始化成功：" + tableName);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "数据表初始化失败", e);
        }
    }
}
