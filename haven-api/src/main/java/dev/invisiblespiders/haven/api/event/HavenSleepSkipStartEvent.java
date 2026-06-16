package dev.invisiblespiders.haven.api.event;

import org.bukkit.World;

public class HavenSleepSkipStartEvent extends HavenEvent {
    private final World world;
    private final int sleeping;
    private final int active;

    public HavenSleepSkipStartEvent(World world, int sleeping, int active) {
        this.world = world;
        this.sleeping = sleeping;
        this.active = active;
    }

    public World getWorld()   { return world; }
    public int getSleeping()  { return sleeping; }
    public int getActive()    { return active; }
}
