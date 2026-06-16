package dev.invisiblespiders.haven.api.service;

import org.bukkit.World;

public interface HavenSleepService {
    /** True only while time is actively advancing (not while paused). */
    boolean isSkipping(World world);
    /** Number of active players currently in beds in this world. */
    int getSleepingCount(World world);
    /** Non-AFK, non-spectator, non-bypass players currently in this world. */
    int getActiveCount(World world);
}
