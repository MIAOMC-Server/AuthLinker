package com.miaomc.authLinker.service;

import com.miaomc.authLinker.AuthLinker;
import com.miaomc.authLinker.database.DatabaseInitializer;
import com.miaomc.authLinker.utils.RSAEncryptor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AuthCommandHandler implements CommandExecutor, TabCompleter {
    private final AuthLinker plugin;
    private final AuthLinkGenerator linkGenerator;
    private final RSAEncryptor rsaEncryptor;
    private final DatabaseInitializer databaseInitializer;
    private final MiniMessage miniMessage;
    private final List<String> validActions = Arrays.asList("login", "suffix");
    private final List<String> subCommands = Arrays.asList("gen", "keygen", "reload", "info", "help");

    public AuthCommandHandler(AuthLinker plugin, AuthLinkGenerator linkGenerator, RSAEncryptor rsaEncryptor, DatabaseInitializer databaseInitializer) {
        this.plugin = plugin;
        this.linkGenerator = linkGenerator;
        this.rsaEncryptor = rsaEncryptor;
        this.databaseInitializer = databaseInitializer;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * 获取并解析MiniMessage格式的消息
     */
    private Component getMessage(String path, String defaultMessage) {
        String message = plugin.getConfig().getString(path, defaultMessage);
        return miniMessage.deserialize(message);
    }

    /**
     * 获取并解析带前缀的MiniMessage格式消息
     */
    private Component getMessageWithPrefix(String path, String defaultMessage) {
        String prefix = plugin.getConfig().getString("settings.prefix", "<white>[<gradient:#00ff00:#ffff00>Auth<gradient:#ffff00:#ff6600>Linker</gradient></gradient>]</white> ");
        String message = plugin.getConfig().getString(path, defaultMessage);
        return miniMessage.deserialize(prefix + message);
    }

    /**
     * 发送MiniMessage格式的消息
     */
    private void sendMessage(CommandSender sender, String path, String defaultMessage) {
        sender.sendMessage(getMessageWithPrefix(path, defaultMessage));
    }

    /**
     * 发送带变量替换的MiniMessage格式消息
     */
    private void sendMessage(CommandSender sender, String path, String defaultMessage, String placeholder, String value) {
        String prefix = plugin.getConfig().getString("settings.prefix", "<white>[<gradient:#00ff00:#ffff00>Auth<gradient:#ffff00:#ff6600>Linker</gradient></gradient>]</white> ");
        String message = plugin.getConfig().getString(path, defaultMessage);
        String finalMessage = prefix + message.replace(placeholder, value);
        sender.sendMessage(miniMessage.deserialize(finalMessage));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "gen":
                return handleGenCommand(sender, args);
            case "keygen":
                return handleKeygenCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "info":
                return handleInfoCommand(sender);
            case "help":
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleGenCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "messages.error.player_only", "<red>此命令只能由玩家执行。</red>");
            return true;
        }

        if (!player.hasPermission("miaomc.authlinker.use")) {
            sendMessage(sender, "messages.error.no_permission", "<red>你没有权限使用此命令。</red>");
            return true;
        }

        if (args.length < 2) {
            sendMessage(sender, "messages.help.gen_usage", "<yellow>生成认证链接: <white>/authlinker gen <action></white></yellow>");
            sendMessage(sender, "messages.help.available_actions", "<yellow>可用操作: <gradient:#ff6600:#ffff00>login</gradient>, <gradient:#ff6600:#ffff00>suffix</gradient></yellow>");
            return true;
        }

        String action = args[1].toLowerCase();
        if (!validActions.contains(action)) {
            sendMessage(sender, "messages.error.invalid_action", "<red>无效的操作类型。支持的操作：<yellow>login, suffix</yellow></red>");
            return true;
        }

        // 异步生成链接
        linkGenerator.generateAuthLink(player, action).thenAccept(result -> {
            if (result.isSuccess()) {
                sendMessage(sender, "messages.success.link_generated", "<gradient:#00ff00:#00ffff>认证链接生成成功！</gradient>");

                // 创建可点击的链接
                String clickMessage = plugin.getConfig().getString("messages.success.click_to_open", "<click:open_url:'{url}'><underlined><aqua>点击打开链接</aqua></underlined></click>");
                String finalClickMessage = clickMessage.replace("{url}", result.getLink());
                player.sendMessage(miniMessage.deserialize(finalClickMessage));
            } else {
                sendMessage(sender, "messages.error.general_error", "<red>生成链接时出错: <yellow>{error}</yellow></red>", "{error}", result.getErrorMessage());
            }
        });

        return true;
    }

    private boolean handleKeygenCommand(CommandSender sender) {
        if (!sender.hasPermission("miaomc.authlinker.admin")) {
            sendMessage(sender, "messages.error.no_permission", "<red>你没有权限使用此命令。</red>");
            return true;
        }

        // 异步生成密钥对
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = rsaEncryptor.generateKeyPair();

            // 在主线程发送消息
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    String keyPath = plugin.getDataFolder().getAbsolutePath() + "/keys/";
                    sendMessage(sender, "messages.success.keygen_success", "<green>RSA密钥对生成成功！密钥文件保存在: <yellow>{path}</yellow></green>", "{path}", keyPath);
                } else {
                    sendMessage(sender, "messages.success.keygen_failure", "<red>密钥对生成失败，请查看控制台错误信息</red>");
                }
            });
        });

        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("miaomc.authlinker.admin")) {
            sendMessage(sender, "messages.error.no_permission", "<red>你没有权限使用此命令。</red>");
            return true;
        }

        try {
            plugin.reloadConfig();
            sendMessage(sender, "messages.reload.success", "<green>配置文件重载成功！</green>");
        } catch (Exception e) {
            sendMessage(sender, "messages.reload.failure", "<red>配置文件重载失败: <yellow>{error}</yellow></red>", "{error}", e.getMessage());
        }

        return true;
    }

    private boolean handleInfoCommand(CommandSender sender) {
        // 发送插件信息
        sendMessage(sender, "messages.info.plugin_info", "<gradient:#00ff00:#00ffff>AuthLinker 插件信息</gradient>");

        // 版本信息
        String version = plugin.getDescription().getVersion();
        sendMessage(sender, "messages.info.version", "<yellow>版本: <white>{version}</white></yellow>", "{version}", version);

        // RSA密钥状态
        String rsaStatusKey = rsaEncryptor.isKeysLoaded() ? "messages.info.keys_loaded" : "messages.info.keys_not_loaded";
        String rsaStatusDefault = rsaEncryptor.isKeysLoaded() ? "<green>已加载</green>" : "<red>未加载</red>";
        String rsaStatus = plugin.getConfig().getString(rsaStatusKey, rsaStatusDefault);
        sendMessage(sender, "messages.info.rsa_status", "<yellow>RSA密钥状态: <white>{status}</white></yellow>", "{status}", rsaStatus);

        // 数据库类型
        sendMessage(sender, "messages.info.database_type", "<yellow>数据库类型: <white>MySQL</white></yellow>");

        // 数据表名
        String tableName = databaseInitializer.getTableName();
        sendMessage(sender, "messages.info.table_name", "<yellow>数据表名: <white>{table_name}</white></yellow>", "{table_name}", tableName);

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sendMessage(sender, "messages.help.usage", "<yellow>用法: <white>/authlinker <子命令></white></yellow>");
        sendMessage(sender, "messages.help.gen_usage", "<yellow>生成认证链接: <white>/authlinker gen <action></white></yellow>");

        if (sender.hasPermission("miaomc.authlinker.admin")) {
            sendMessage(sender, "messages.help.keygen_usage", "<yellow>生成密钥对: <white>/authlinker keygen</white></yellow>");
            sendMessage(sender, "messages.help.reload_usage", "<yellow>重载配置: <white>/authlinker reload</white></yellow>");
        }

        sendMessage(sender, "messages.help.info_usage", "<yellow>查看信息: <white>/authlinker info</white></yellow>");
        sendMessage(sender, "messages.help.available_actions", "<yellow>可用操作: <gradient:#ff6600:#ffff00>login</gradient>, <gradient:#ff6600:#ffff00>suffix</gradient></yellow>");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 第一个参数：子命令
            for (String subCmd : subCommands) {
                if (subCmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    // 检查权限
                    if ((subCmd.equals("keygen") || subCmd.equals("reload")) && !sender.hasPermission("miaomc.authlinker.admin")) {
                        continue;
                    }
                    if (subCmd.equals("gen") && !sender.hasPermission("miaomc.authlinker.use")) {
                        continue;
                    }
                    completions.add(subCmd);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("gen")) {
            // 第二个参数：gen 命令的操作类型
            if (sender.hasPermission("miaomc.authlinker.use")) {
                for (String action : validActions) {
                    if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(action);
                    }
                }
            }
        }

        return completions;
    }
}
