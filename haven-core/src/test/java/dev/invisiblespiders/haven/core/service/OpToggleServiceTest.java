package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.core.config.OpToggleSettings;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class OpToggleServiceTest {

    @Test
    void registersConfiguredPermissionsWithFalseDefault() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Plugin plugin = mockPlugin();
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        when(pluginManager.getPermission("havencore.toggleop.a5b27")).thenReturn(null);
        OpToggleService service = new OpToggleService(plugin, settings(true, entry("InvisibleSpiders", uuid, "A5B27")));

        service.registerPermissions();

        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        verify(pluginManager).addPermission(permissionCaptor.capture());
        Permission permission = permissionCaptor.getValue();
        assertEquals("havencore.toggleop.a5b27", permission.getName());
        assertFalse(permission.getDefault().getValue(false));
    }

    @Test
    void rejectsWhenFeatureDisabled() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        OpToggleService service = new OpToggleService(
            mockPlugin(),
            settings(false, entry("InvisibleSpiders", uuid, "A5B27"))
        );
        Player player = mockPlayer(uuid, true, false);

        OpToggleService.ToggleResult result = service.toggle(player);

        assertFalse(result.allowed());
        verify(player, never()).setOp(anyBoolean());
    }

    @Test
    void rejectsWhenUuidOrPermissionDoesNotMatch() {
        UUID allowedUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID otherUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        OpToggleService service = new OpToggleService(
            mockPlugin(),
            settings(true, entry("InvisibleSpiders", allowedUuid, "A5B27"))
        );

        assertFalse(service.toggle(mockPlayer(otherUuid, true, false)).allowed());
        assertFalse(service.toggle(mockPlayer(allowedUuid, false, false)).allowed());
    }

    @Test
    void togglesOpForMatchingUuidAndPermission() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        OpToggleService service = new OpToggleService(
            mockPlugin(),
            settings(true, entry("InvisibleSpiders", uuid, "A5B27"))
        );
        Player player = mockPlayer(uuid, true, false);
        when(player.getName()).thenReturn("InvisibleSpiders");

        OpToggleService.ToggleResult result = service.toggle(player);

        assertTrue(result.allowed());
        assertEquals(Optional.of(true), result.newOpState());
        verify(player).setOp(true);
    }

    private static Plugin mockPlugin() {
        Plugin plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        return plugin;
    }

    private static Player mockPlayer(UUID uuid, boolean hasPermission, boolean op) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission("havencore.toggleop.a5b27")).thenReturn(hasPermission);
        when(player.isOp()).thenReturn(op);
        return player;
    }

    private static OpToggleSettings settings(boolean enabled, OpToggleSettings.Entry entry) {
        return new OpToggleSettings(enabled, List.of(entry));
    }

    private static OpToggleSettings.Entry entry(String name, UUID uuid, String code) {
        return new OpToggleSettings.Entry(name, uuid, code);
    }
}
