package dev.invisiblespiders.haven.api.service;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface HavenMessageService {

    Component getJoinMessage(Player player);

    Component getQuitMessage(Player player);

    Component getAfkMessage(Player player);

    Component getDeathMessage(Player player, EntityDamageEvent.DamageCause cause,
                               @Nullable Entity killer);

    void unlockPreset(UUID uuid, String presetId);

    List<String> getUnlockedPresets(UUID uuid);
}
