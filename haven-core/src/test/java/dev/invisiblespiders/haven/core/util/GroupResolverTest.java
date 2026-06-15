package dev.invisiblespiders.haven.core.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupResolverTest {

    @Mock LuckPerms luckPerms;
    @Mock PlayerAdapter<Player> playerAdapter;
    @Mock User user;
    @Mock CachedMetaData metaData;
    @Mock Player player;

    @Test
    void returnsDefaultWhenLuckPermsNull() {
        GroupResolver resolver = new GroupResolver(null);
        assertThat(resolver.getPrimaryGroup(player)).isEqualTo("default");
    }

    @Test
    void returnsEmptyPrefixWhenLuckPermsNull() {
        GroupResolver resolver = new GroupResolver(null);
        assertThat(resolver.getPrefix(player)).isEmpty();
    }

    @Test
    void returnsPrimaryGroupFromLuckPerms() {
        when(luckPerms.getPlayerAdapter(Player.class)).thenReturn(playerAdapter);
        when(playerAdapter.getUser(player)).thenReturn(user);
        when(user.getPrimaryGroup()).thenReturn("vip");

        GroupResolver resolver = new GroupResolver(luckPerms);
        assertThat(resolver.getPrimaryGroup(player)).isEqualTo("vip");
    }

    @Test
    void returnsPrefixFromLuckPerms() {
        when(luckPerms.getPlayerAdapter(Player.class)).thenReturn(playerAdapter);
        when(playerAdapter.getUser(player)).thenReturn(user);
        when(user.getCachedData()).thenReturn(mock(net.luckperms.api.cacheddata.CachedDataManager.class));
        when(user.getCachedData().getMetaData()).thenReturn(metaData);
        when(metaData.getPrefix()).thenReturn("[VIP]");

        GroupResolver resolver = new GroupResolver(luckPerms);
        assertThat(resolver.getPrefix(player)).isEqualTo("[VIP]");
    }

    @Test
    void returnsEmptyPrefixWhenPrefixNull() {
        when(luckPerms.getPlayerAdapter(Player.class)).thenReturn(playerAdapter);
        when(playerAdapter.getUser(player)).thenReturn(user);
        when(user.getCachedData()).thenReturn(mock(net.luckperms.api.cacheddata.CachedDataManager.class));
        when(user.getCachedData().getMetaData()).thenReturn(metaData);
        when(metaData.getPrefix()).thenReturn(null);

        GroupResolver resolver = new GroupResolver(luckPerms);
        assertThat(resolver.getPrefix(player)).isEmpty();
    }

    @Test
    void returnsDefaultWhenUserNull() {
        when(luckPerms.getPlayerAdapter(Player.class)).thenReturn(playerAdapter);
        when(playerAdapter.getUser(player)).thenReturn(null);

        GroupResolver resolver = new GroupResolver(luckPerms);
        assertThat(resolver.getPrimaryGroup(player)).isEqualTo("default");
    }

    @Test
    void returnsEmptyPrefixWhenUserNull() {
        when(luckPerms.getPlayerAdapter(Player.class)).thenReturn(playerAdapter);
        when(playerAdapter.getUser(player)).thenReturn(null);

        GroupResolver resolver = new GroupResolver(luckPerms);
        assertThat(resolver.getPrefix(player)).isEmpty();
    }

    @Test
    void returnsEmptyPrefixWhenMetaDataNull() {
        when(luckPerms.getPlayerAdapter(Player.class)).thenReturn(playerAdapter);
        when(playerAdapter.getUser(player)).thenReturn(user);
        var cdm = mock(net.luckperms.api.cacheddata.CachedDataManager.class);
        when(user.getCachedData()).thenReturn(cdm);
        when(cdm.getMetaData()).thenReturn(null);

        GroupResolver resolver = new GroupResolver(luckPerms);
        assertThat(resolver.getPrefix(player)).isEmpty();
    }
}
