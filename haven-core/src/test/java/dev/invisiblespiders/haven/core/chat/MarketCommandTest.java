package dev.invisiblespiders.haven.core.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.invisiblespiders.haven.api.service.HavenCooldownService;
import dev.invisiblespiders.haven.api.service.HavenWarpService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class MarketCommandTest {
    @Test
    void advertiseUsesLatestWarpServiceFromSupplier() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("ShopOwner");
        when(player.hasPermission("haven.market.advertise")).thenReturn(true);
        HavenWarpService unavailable = mock(HavenWarpService.class);
        when(unavailable.hasShopWarp(playerId, "tools")).thenReturn(false);
        HavenWarpService available = mock(HavenWarpService.class);
        when(available.hasShopWarp(playerId, "tools")).thenReturn(true);
        AtomicReference<HavenWarpService> warpService = new AtomicReference<>(unavailable);
        HavenCooldownService cooldown = mock(HavenCooldownService.class);
        MarketQueue queue = mock(MarketQueue.class);
        ChatSettings.MarketConfig config = new ChatSettings.MarketConfig(
                300, 30, "&7[Market] {player}: {message}", "/warp %s", "<yellow>Visit %s");
        MarketCommand command = new MarketCommand(queue, config, warpService::get, cooldown);

        command.onCommand(player, mock(Command.class), "market", new String[] {"advertise", "tools", "Selling", "stone"});
        verify(queue, never()).enqueue(any());

        warpService.set(available);
        command.onCommand(player, mock(Command.class), "market", new String[] {"advertise", "tools", "Selling", "stone"});

        verify(queue).enqueue(any(AdvertisementRequest.class));
    }

    @Test
    void tabCompletionUsesLatestWarpServiceFromSupplier() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        HavenWarpService service = mock(HavenWarpService.class);
        when(service.getShopWarps(playerId)).thenReturn(List.of("tools", "food"));
        MarketCommand command = new MarketCommand(
                mock(MarketQueue.class),
                new ChatSettings.MarketConfig(300, 30, "", "/warp %s", "<yellow>Visit %s"),
                () -> service,
                mock(HavenCooldownService.class)
        );

        List<String> completions = command.onTabComplete(
                player, mock(Command.class), "market", new String[] {"advertise", "to"});

        org.junit.jupiter.api.Assertions.assertEquals(List.of("tools"), completions);
    }
}
