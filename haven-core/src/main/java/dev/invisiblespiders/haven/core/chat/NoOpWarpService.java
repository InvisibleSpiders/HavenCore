package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.service.HavenWarpService;

import java.util.List;
import java.util.UUID;

public final class NoOpWarpService implements HavenWarpService {
    @Override public List<String> getShopWarps(UUID playerUuid) { return List.of(); }
    @Override public boolean hasShopWarp(UUID playerUuid, String warpName) { return false; }
}
