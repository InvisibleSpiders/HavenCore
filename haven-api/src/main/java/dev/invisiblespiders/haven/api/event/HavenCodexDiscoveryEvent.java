package dev.invisiblespiders.haven.api.event;

import dev.invisiblespiders.haven.api.model.CodexEntry;

import java.util.UUID;

public class HavenCodexDiscoveryEvent extends HavenEvent {
    private final UUID playerUuid;
    private final CodexEntry entry;
    public HavenCodexDiscoveryEvent(UUID playerUuid, CodexEntry entry) {
        this.playerUuid = playerUuid;
        this.entry = entry;
    }
    public UUID getPlayerUuid() { return playerUuid; }
    public CodexEntry getEntry() { return entry; }
}
