package dev.invisiblespiders.haven.api.service;

import org.bukkit.World;

public interface HavenSleepService {
    boolean isSkipping(World world);
    int getSleepingCount(World world);
    int getActiveCount(World world);
}
