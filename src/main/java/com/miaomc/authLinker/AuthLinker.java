package com.miaomc.authLinker;

import com.miaomc.authLinker.database.AuthRecordManager;
import com.miaomc.authLinker.database.DatabaseInitializer;
import com.miaomc.authLinker.database.DatabaseManager;
import com.miaomc.authLinker.service.AuthCommandHandler;
import com.miaomc.authLinker.service.AuthLinkGenerator;
import com.miaomc.authLinker.utils.RSAEncryptor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuthLinker extends JavaPlugin {
    private DatabaseManager databaseManager;
    private RSAEncryptor rsaEncryptor;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 初始化RSA加密器
        rsaEncryptor = new RSAEncryptor(this);

        // 初始化数据库连接和表结构
        databaseManager = new DatabaseManager(this);
        DatabaseInitializer databaseInitializer = new DatabaseInitializer(this, databaseManager);
        databaseInitializer.initializeDatabase();

        // 初始化记录管理器和链接生成器（传递databaseInitializer）
        AuthRecordManager authRecordManager = new AuthRecordManager(this, databaseManager, databaseInitializer);
        AuthLinkGenerator authLinkGenerator = new AuthLinkGenerator(this, authRecordManager, rsaEncryptor);

        // 注册命令（传递databaseInitializer以获取表名）
        AuthCommandHandler commandHandler = new AuthCommandHandler(this, authLinkGenerator, rsaEncryptor, databaseInitializer);
        registerCommands(commandHandler);

        getLogger().info("AuthLinker 插件已启用!");

        // 检查密钥状态
        if (!rsaEncryptor.isKeysLoaded()) {
            getLogger().warning("RSA密钥未加载！请使用 /authlinker keygen 命令生成密钥对");
        } else {
            getLogger().info("RSA密钥已成功加载");
        }
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

    /**
     * 获取RSA加密器实例
     */
    public RSAEncryptor getRsaEncryptor() {
        return rsaEncryptor;
    }
}
