package dev.invisiblespiders.haven.core.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class GroupResolver {

    private final @Nullable LuckPerms luckPerms;

    public GroupResolver(@Nullable LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public String getPrimaryGroup(Player player) {
        if (luckPerms == null) return "default";
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        return user == null ? "default" : user.getPrimaryGroup();
    }

    public String getPrefix(Player player) {
        if (luckPerms == null) return "";
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        if (user == null) return "";
        CachedMetaData meta = user.getCachedData().getMetaData();
        if (meta == null) return "";
        String prefix = meta.getPrefix();
        return prefix != null ? prefix : "";
    }
}
