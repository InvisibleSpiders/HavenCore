package dev.invisiblespiders.haven.api.service;

import java.util.List;
import java.util.UUID;

/**
 * Stub interface — returns empty results until HavenWarps registers an implementation.
 * Chat system uses this for market advertisement validation and tab-complete.
 */
public interface HavenWarpService {

    /** Returns the names of all shop warps owned by this player. Empty until HavenWarps is present. */
    List<String> getShopWarps(UUID playerUuid);

    /** Returns true if the player owns a shop warp with this name. Always false until HavenWarps is present. */
    boolean hasShopWarp(UUID playerUuid, String warpName);
}
