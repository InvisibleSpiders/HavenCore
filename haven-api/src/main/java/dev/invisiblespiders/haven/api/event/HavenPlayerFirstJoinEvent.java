package dev.invisiblespiders.haven.api.event;

import dev.invisiblespiders.haven.api.model.HavenPlayer;

public class HavenPlayerFirstJoinEvent extends HavenEvent {
    private final HavenPlayer player;
    public HavenPlayerFirstJoinEvent(HavenPlayer player) { this.player = player; }
    public HavenPlayer getPlayer() { return player; }
}
