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

        // 构建MySQL连接URL
        String host = config.getString("database.mysql.host", "localhost");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "authlinker");
        boolean ssl = config.getBoolean("database.mysql.ssl", false);

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + ssl + "&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getString("database.mysql.username", "root"));
        hikariConfig.setPassword(config.getString("database.mysql.password", ""));
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 连接池设置
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setPoolName("AuthLinkerPool");

        // 性能优化设置
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("MySQL数据库连接池初始化成功");
        } catch (Exception e) {
            plugin.getLogger().severe("MySQL数据库连接池初始化失败: " + e.getMessage());
            throw new RuntimeException("无法初始化数据库连接", e);
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("数据库连接池未初始化或已关闭");
        }
        return dataSource.getConnection();
    }

    /**
     * 关闭数据源
     */
    public void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("数据库连接池已关闭");
        }
    }

    /**
     * 检查数据库连接是否正常
     */
    public boolean isConnectionValid() {
        try (Connection connection = getConnection()) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            plugin.getLogger().warning("数据库连接检查失败: " + e.getMessage());
            return false;
        }
    }
}
