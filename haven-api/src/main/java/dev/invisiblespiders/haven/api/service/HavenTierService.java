package dev.invisiblespiders.haven.api.service;

import org.bukkit.entity.Player;

import java.util.Optional;

public interface HavenTierService {

    /**
     * Returns the player's tier name as configured in config.yml.
     * Reads LuckPerms meta key first; falls back to permission node check.
     * Returns "default" if no tier matches.
     */
    String getTier(Player player);

    /**
     * Reads an arbitrary LuckPerms meta value for the player.
     * Empty if LuckPerms is unavailable or the key is not set.
     */
    Optional<String> getMeta(Player player, String key);

    /**
     * Returns the highest numeric value the player has among the given
     * permission-keyed map. Used for limit resolution across tiers.
     *
     * Example: resolveHighest(player, Map.of("havenclaims.limit.default", 10,
     *                                         "havenclaims.limit.vip", 75))
     */
    int resolveHighest(Player player, java.util.Map<String, Integer> permissionValues);
}
