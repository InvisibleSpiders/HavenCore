package dev.invisiblespiders.haven.core.placeholder;

import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.core.util.GroupResolver;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HavenExpansion extends PlaceholderExpansion {

    private final HavenAfkService afkService;
    private final GroupResolver groupResolver;

    public HavenExpansion(HavenAfkService afkService, GroupResolver groupResolver) {
        this.afkService = afkService;
        this.groupResolver = groupResolver;
    }

    @Override public @NotNull String getIdentifier() { return "haven"; }
    @Override public @NotNull String getAuthor() { return "InvisibleSpiders"; }
    @Override public @NotNull String getVersion() { return "1.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        return switch (params) {
            case "afk"          -> String.valueOf(afkService.isAfk(player.getUniqueId()));
            case "afk_time"     -> String.valueOf(afkService.getIdleSeconds(player.getUniqueId()));
            case "rank_prefix"  -> groupResolver.getPrefix(player);
            case "rank_group"   -> groupResolver.getPrimaryGroup(player);
            default             -> null;
        };
    }
}
