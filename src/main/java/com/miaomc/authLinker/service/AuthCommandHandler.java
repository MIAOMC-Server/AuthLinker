package com.miaomc.authLinker.service;

import com.miaomc.authLinker.AuthLinker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    private final String prefix;
    private final List<String> validActions = Arrays.asList("login", "suffix");

    public AuthCommandHandler(AuthLinker plugin, AuthLinkGenerator linkGenerator) {
        this.plugin = plugin;
        this.linkGenerator = linkGenerator;
        this.prefix = plugin.getConfig().getString("settings.prefix", "§f[§aAuth§6Linker§f] §r");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + "此命令只能由玩家执行。");
            return true;
        }

        if (!player.hasPermission("miaomc.authlinker.use")) {
            player.sendMessage(Component.text(prefix + "你没有权限使用此命令。", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("gen")) {
            sendUsage(player);
            return true;
        }

        String action = args[1].toLowerCase();
        if (!validActions.contains(action)) {
            player.sendMessage(Component.text(prefix + "无效的操作类型。支持的操作：")
                    .append(Component.text("login, suffix", NamedTextColor.YELLOW)));
            return true;
        }

        // 异步生成链接
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> linkGenerator.generateAuthLink(player, action)
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (result.isSuccess()) {
                        // 发送可点击链接
                        sendClickableLink(player, result.getLink(), action);
                    } else {
                        // 发送错误信息
                        player.sendMessage(Component.text(prefix + result.getErrorMessage(), NamedTextColor.RED));
                    }
                })));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("gen");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("gen")) {
            completions.addAll(validActions);
        }

        return completions;
    }

    private void sendUsage(Player player) {
        Component message = Component.text(prefix + "用法: ", NamedTextColor.WHITE)
                .append(Component.text("/" + (player.hasPermission("miaomc.authlinker.admin") ? "authlinker" : "al"), NamedTextColor.GOLD))
                .append(Component.text(" gen <action>", NamedTextColor.YELLOW));
        player.sendMessage(message);
    }

    private void sendClickableLink(Player player, String link, String action) {
        // 确保链接以http://或https://开头
        if (!link.toLowerCase().startsWith("http://") && !link.toLowerCase().startsWith("https://")) {
            link = "http://" + link;
        }

        plugin.getLogger().info("生成的验证链接: " + link); // 添加日志以便调试

        Component message = Component.text(prefix + "已生成" + action + "验证链接：", NamedTextColor.GREEN)
                .append(Component.newline())
                .append(Component.text("点击打开链接", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(link)));

        player.sendMessage(message);
    }
}
