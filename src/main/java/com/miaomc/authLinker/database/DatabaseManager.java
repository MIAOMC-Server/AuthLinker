package com.miaomc.authLinker.database;

import com.miaomc.authLinker.AuthLinker;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private final AuthLinker plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(AuthLinker plugin) {
        this.plugin = plugin;
        initializeDataSource();
    }

    private void initializeDataSource() {
        FileConfiguration config = plugin.getConfig();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" +
                config.getString("database.host") + ":" +
                config.getInt("database.port") + "/" +
                config.getString("database.name") +
                "?useSSL=false&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
        hikariConfig.setUsername(config.getString("database.username"));
        hikariConfig.setPassword(config.getString("database.password"));
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 基本连接池设置
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(config.getLong("settings.timeout"));
        hikariConfig.setIdleTimeout(30000);
        hikariConfig.setPoolName("AuthLinkerPool");

        dataSource = new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
