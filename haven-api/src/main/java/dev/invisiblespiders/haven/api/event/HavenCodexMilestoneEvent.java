package dev.invisiblespiders.haven.api.event;

import dev.invisiblespiders.haven.api.model.CodexCategory;

import java.util.UUID;

public class HavenCodexMilestoneEvent extends HavenEvent {
    private final UUID playerUuid;
    private final CodexCategory category;
    private final int count;
    public HavenCodexMilestoneEvent(UUID playerUuid, CodexCategory category, int count) {
        this.playerUuid = playerUuid;
        this.category = category;
        this.count = count;
    }
    public UUID getPlayerUuid() { return playerUuid; }
    public CodexCategory getCategory() { return category; }
    public int getCount() { return count; }
}
