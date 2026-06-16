package dev.invisiblespiders.haven.api.event;

import org.bukkit.World;

public class HavenSleepSkipCompleteEvent extends HavenEvent {
    private final World world;

    public HavenSleepSkipCompleteEvent(World world) {
        this.world = world;
    }

    public World getWorld() { return world; }
}
