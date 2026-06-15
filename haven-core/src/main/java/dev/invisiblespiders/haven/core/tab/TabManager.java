package dev.invisiblespiders.haven.core.tab;

import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.core.hook.PlaceholderAPIHook;
import dev.invisiblespiders.haven.core.util.GroupResolver;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class TabManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TabSettings settings;
    private final HavenAfkService afkService;
    private final GroupResolver groupResolver;
    private final PlaceholderAPIHook papiHook;
    private final Plugin plugin;

    private @Nullable BukkitTask refreshTask;

    public TabManager(TabSettings settings, HavenAfkService afkService,
                      GroupResolver groupResolver, PlaceholderAPIHook papiHook, Plugin plugin) {
        this.settings = settings;
        this.afkService = afkService;
        this.groupResolver = groupResolver;
        this.papiHook = papiHook;
        this.plugin = plugin;
    }

    public void start() {
        long intervalTicks = (long) settings.refreshInterval() * 20L;
        refreshTask = new BukkitRunnable() {
            @Override public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    sendHeaderFooter(p);
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            sendHeaderFooter(player);
            refreshPlayer(player);
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (!other.equals(player)) sendHeaderFooter(other);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.getPlayer().playerListName(null);
    }

    public void refreshPlayer(Player player) {
        String group = groupResolver.getPrimaryGroup(player);
        String template = resolveDisplayName(player, group);
        String resolved = template.replace("<player_name>", player.getName());
        player.playerListName(MM.deserialize(resolved));
    }

    /** Package-visible for tests — resolves the raw format string (no MiniMessage deserialization). */
    String resolveDisplayName(Player player, String group) {
        if (afkService.isAfk(player.getUniqueId())) {
            return settings.afkFormat();
        }
        return settings.playerFormat().resolveFormat(group);
    }

    private void sendHeaderFooter(Player player) {
        Component header = buildLines(settings.header(), player);
        Component footer = buildLines(settings.footer(), player);
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private Component buildLines(List<String> lines, Player player) {
        if (lines.isEmpty()) return Component.empty();
        String joined = lines.stream()
                .map(line -> papiHook.isAvailable()
                        ? PlaceholderAPI.setPlaceholders(player, line) : line)
                .collect(Collectors.joining("\n"));
        return MM.deserialize(joined);
    }
}
