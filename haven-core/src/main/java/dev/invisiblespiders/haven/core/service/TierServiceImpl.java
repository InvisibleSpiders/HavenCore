package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.service.HavenTierService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.MetaNode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class TierServiceImpl implements HavenTierService {

    private final String metaKey;
    private final Logger logger;

    public TierServiceImpl(String metaKey, Logger logger) {
        this.metaKey = metaKey;
        this.logger = logger;
    }

    @Override
    public String getTier(Player player) {
        return getMeta(player, metaKey).orElse("default");
    }

    @Override
    public Optional<String> getMeta(Player player, String key) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getPlayerAdapter(Player.class).getUser(player);
            return user.getCachedData().getMetaData().getMetaValue(key, String.class)
                .map(Optional::of)
                .orElse(Optional.empty());
        } catch (IllegalStateException e) {
            // LuckPerms not available
            return Optional.empty();
        } catch (Exception e) {
            logger.fine("TierService getMeta error for key=" + key + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int resolveHighest(Player player, Map<String, Integer> permissionValues) {
        int highest = 0;
        for (Map.Entry<String, Integer> entry : permissionValues.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                highest = Math.max(highest, entry.getValue());
            }
        }
        return highest;
    }
}
