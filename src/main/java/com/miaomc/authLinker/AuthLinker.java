package com.miaomc.authLinker;

import com.miaomc.authLinker.database.AuthRecordManager;
import com.miaomc.authLinker.database.DatabaseInitializer;
import com.miaomc.authLinker.database.DatabaseManager;
import com.miaomc.authLinker.service.AuthCommandHandler;
import com.miaomc.authLinker.service.AuthLinkGenerator;
import com.miaomc.authLinker.utils.Base64Obfuscator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuthLinker extends JavaPlugin {
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 初始化混淆器
        Base64Obfuscator base64Obfuscator = new Base64Obfuscator(getConfig());

        // 初始化数据库连接和表结构
        databaseManager = new DatabaseManager(this);
        DatabaseInitializer databaseInitializer = new DatabaseInitializer(this, databaseManager);
        databaseInitializer.initializeDatabase();

        // 初始化记录管理器和链接生成器
        AuthRecordManager authRecordManager = new AuthRecordManager(this, databaseManager);
        AuthLinkGenerator authLinkGenerator = new AuthLinkGenerator(this, authRecordManager, base64Obfuscator);

        // 注册命令
        AuthCommandHandler commandHandler = new AuthCommandHandler(this, authLinkGenerator);
        registerCommands(commandHandler);

        getLogger().info("AuthLinker 插件已启用!");
    }

    @Override
    public void onDisable() {
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.closeDataSource();
        }

        getLogger().info("AuthLinker 插件已禁用!");
    }

    private void registerCommands(AuthCommandHandler commandHandler) {
        // 注册主命令
        PluginCommand authlinkerCmd = getCommand("authlinker");
        if (authlinkerCmd != null) {
            authlinkerCmd.setExecutor(commandHandler);
            authlinkerCmd.setTabCompleter(commandHandler);
        } else {
            getLogger().warning("无法注册 authlinker 命令!");
        }

        // 注册别名命令
        PluginCommand alCmd = getCommand("al");
        if (alCmd != null) {
            alCmd.setExecutor(commandHandler);
            alCmd.setTabCompleter(commandHandler);
        } else {
            getLogger().warning("无法注册 al 命令!");
        }
    }
}
