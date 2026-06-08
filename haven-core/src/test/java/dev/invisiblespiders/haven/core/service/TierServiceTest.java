package dev.invisiblespiders.haven.core.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TierServiceTest {

    @Test
    void resolveHighestReturnsHighestGrantedPermission() {
        TierServiceImpl service = new TierServiceImpl("haven_tier", java.util.logging.Logger.getAnonymousLogger());

        var player = mock(org.bukkit.entity.Player.class);
        when(player.hasPermission("haven.limit.default")).thenReturn(true);
        when(player.hasPermission("haven.limit.vip")).thenReturn(true);
        when(player.hasPermission("haven.limit.elite")).thenReturn(false);

        Map<String, Integer> limits = Map.of(
            "haven.limit.default", 10,
            "haven.limit.vip",     75,
            "haven.limit.elite",  150
        );

        assertEquals(75, service.resolveHighest(player, limits));
    }

    @Test
    void resolveHighestReturnsZeroIfNoPermissions() {
        TierServiceImpl service = new TierServiceImpl("haven_tier", java.util.logging.Logger.getAnonymousLogger());
        var player = mock(org.bukkit.entity.Player.class);
        when(player.hasPermission(anyString())).thenReturn(false);

        assertEquals(0, service.resolveHighest(player, Map.of("haven.limit.default", 10)));
    }
}
