package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public class MarketQueue {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Deque<AdvertisementRequest> queue = new ArrayDeque<>();
    private @Nullable BukkitTask task;

    public MarketQueue() {}

    public void start(ChatSettings.MarketConfig marketConfig, HavenPlayerService playerService, Plugin plugin) {
        long intervalTicks = (long) marketConfig.slotInterval() * 20L;
        task = new BukkitRunnable() {
            @Override public void run() {
                AdvertisementRequest req;
                synchronized (queue) { req = queue.poll(); }
                if (req == null) return;
                broadcast(req, marketConfig, playerService, plugin);
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public synchronized void enqueue(AdvertisementRequest request) {
        queue.addLast(request);
    }

    public synchronized @Nullable AdvertisementRequest poll() {
        return queue.poll();
    }

    public synchronized boolean isEmpty() { return queue.isEmpty(); }
    public synchronized int size() { return queue.size(); }

    private void broadcast(AdvertisementRequest req, ChatSettings.MarketConfig config,
                           HavenPlayerService playerService, Plugin plugin) {
        String command = config.warpCommand().formatted(req.shopWarpName());
        String hoverText = config.warpHover().formatted(req.shopWarpName());
        Component warpComponent = MM.deserialize("<click:run_command:'" + command + "'>"
                + req.shopWarpName() + "</click>")
                .hoverEvent(HoverEvent.showText(MM.deserialize(hoverText)));

        // Replace the <shopwarp> placeholder with a sentinel, then splice the clickable component in
        String templateWithoutWarp = config.format().replace("<shopwarp>", "WARP_PLACEHOLDER");
        Component pre = MM.deserialize(templateWithoutWarp,
                Placeholder.unparsed("player_name", req.playerName()),
                Placeholder.unparsed("message", req.message()));

        Component broadcast = spliceWarp(pre, warpComponent);

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            Optional<HavenPlayer> hp = playerService.getCached(online.getUniqueId());
            boolean muted = hp.map(p -> "true".equals(
                    p.getData("haven-core", "market-muted").orElse(""))).orElse(false);
            if (!muted) online.sendMessage(broadcast);
        }
    }

    private Component spliceWarp(Component pre, Component warpComponent) {
        String plain = PlainTextComponentSerializer.plainText().serialize(pre);
        int idx = plain.indexOf("WARP_PLACEHOLDER");
        if (idx < 0) return pre.append(Component.text(" ")).append(warpComponent);
        return Component.text(plain.substring(0, idx))
                .append(warpComponent)
                .append(Component.text(plain.substring(idx + "WARP_PLACEHOLDER".length())));
    }
}
