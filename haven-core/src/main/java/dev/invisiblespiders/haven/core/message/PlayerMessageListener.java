package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.service.HavenMessageService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerMessageListener implements Listener {

    private final HavenMessageService messageService;

    public PlayerMessageListener(HavenMessageService messageService) {
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        player.getServer().getOnlinePlayers().forEach(p ->
                p.sendMessage(messageService.getJoinMessage(player)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        Player player = event.getPlayer();
        player.getServer().getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .forEach(p -> p.sendMessage(messageService.getQuitMessage(player)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        Player player = event.getEntity();
        var lastDamage = player.getLastDamageCause();
        var cause = lastDamage != null
                ? lastDamage.getCause()
                : EntityDamageEvent.DamageCause.CUSTOM;
        Entity killer = player.getKiller();
        player.getServer().getOnlinePlayers().forEach(p ->
                p.sendMessage(messageService.getDeathMessage(player, cause, killer)));
    }
}
