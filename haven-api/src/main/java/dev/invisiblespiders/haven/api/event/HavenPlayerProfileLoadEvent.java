package dev.invisiblespiders.haven.api.event;

import dev.invisiblespiders.haven.api.model.HavenPlayer;

public class HavenPlayerProfileLoadEvent extends HavenEvent {
    private final HavenPlayer player;
    private final boolean firstJoin;
    public HavenPlayerProfileLoadEvent(HavenPlayer player, boolean firstJoin) {
        this.player = player;
        this.firstJoin = firstJoin;
    }
    public HavenPlayer getPlayer() { return player; }
    public boolean isFirstJoin() { return firstJoin; }
}
