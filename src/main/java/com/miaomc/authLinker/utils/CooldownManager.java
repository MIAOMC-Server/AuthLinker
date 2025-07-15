package com.miaomc.authLinker.utils;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冷却时间管理器
 * 使用内存缓存来管理玩家操作的冷却时间，避免频繁的数据库查询
 */
public class CooldownManager {

    // 使用ConcurrentHashMap保证线程安全
    private final Map<String, Long> cooldownCache = new ConcurrentHashMap<>();
    private final int cooldownTime; // 冷却时间（秒）

    public CooldownManager(FileConfiguration config) {
        this.cooldownTime = config.getInt("settings.cooldown", 120);
    }

    /**
     * 生成缓存键
     * @param playerUUID 玩家UUID
     * @param action 操作类型
     * @return 缓存键
     */
    private String generateCacheKey(UUID playerUUID, String action) {
        return playerUUID.toString() + ":" + action;
    }

    /**
     * 检查玩家是否在冷却期内
     * @param playerUUID 玩家UUID
     * @param action 操作类型
     * @return 是否在冷却期内
     */
    public boolean isInCooldown(UUID playerUUID, String action) {
        String cacheKey = generateCacheKey(playerUUID, action);
        Long lastActionTime = cooldownCache.get(cacheKey);

        if (lastActionTime == null) {
            // 没有记录，不在冷却期
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long timeDifference = currentTime - lastActionTime;
        long cooldownTimeMillis = cooldownTime * 1000L;

        if (timeDifference >= cooldownTimeMillis) {
            // 冷却期已过，移除过期记录
            cooldownCache.remove(cacheKey);
            return false;
        }

        // 还在冷却期内
        return true;
    }

    /**
     * 获取剩余冷却时间（秒）
     * @param playerUUID 玩家UUID
     * @param action 操作类型
     * @return 剩余冷却时间（秒），如果不在冷却期则返回0
     */
    public int getRemainingCooldown(UUID playerUUID, String action) {
        String cacheKey = generateCacheKey(playerUUID, action);
        Long lastActionTime = cooldownCache.get(cacheKey);

        if (lastActionTime == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long timeDifference = currentTime - lastActionTime;
        long cooldownTimeMillis = cooldownTime * 1000L;

        if (timeDifference >= cooldownTimeMillis) {
            // 冷却期已过
            cooldownCache.remove(cacheKey);
            return 0;
        }

        // 计算剩余时间（向上取整）
        long remainingMillis = cooldownTimeMillis - timeDifference;
        return (int) Math.ceil(remainingMillis / 1000.0);
    }

    /**
     * 记录玩家执行操作的时间
     * @param playerUUID 玩家UUID
     * @param action 操作类型
     */
    public void recordAction(UUID playerUUID, String action) {
        String cacheKey = generateCacheKey(playerUUID, action);
        long currentTime = System.currentTimeMillis();
        cooldownCache.put(cacheKey, currentTime);
    }

    /**
     * 清理过期的冷却记录
     * 这个方法可以定期调用来清理内存
     */
    public void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        long cooldownTimeMillis = cooldownTime * 1000L;

        cooldownCache.entrySet().removeIf(entry -> {
            long timeDifference = currentTime - entry.getValue();
            return timeDifference >= cooldownTimeMillis;
        });
    }

    /**
     * 清除指定玩家的所有冷却记录
     * @param playerUUID 玩家UUID
     */
    public void clearPlayerCooldowns(UUID playerUUID) {
        String playerPrefix = playerUUID.toString() + ":";
        cooldownCache.entrySet().removeIf(entry ->
            entry.getKey().startsWith(playerPrefix));
    }

    /**
     * 获取当前缓存的记录数量（用于监控）
     * @return 缓存记录数量
     */
    public int getCacheSize() {
        return cooldownCache.size();
    }

    /**
     * 清空所有冷却记录
     */
    public void clearAllCooldowns() {
        cooldownCache.clear();
    }
}
