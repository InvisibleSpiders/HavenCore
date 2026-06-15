package dev.invisiblespiders.haven.api.service;

import java.util.UUID;

public interface HavenAfkService {

    /** Returns true if the player is currently marked AFK. */
    boolean isAfk(UUID uuid);

    /**
     * Returns how many seconds the player has been idle.
     * Returns 0 if the player is not online or not tracked.
     */
    long getIdleSeconds(UUID uuid);

    /**
     * Admin override — forcibly set a player's AFK state.
     * Triggers the same broadcast/action-bar/tab effects as organic AFK detection.
     */
    void setAfk(UUID uuid, boolean afk);
}
