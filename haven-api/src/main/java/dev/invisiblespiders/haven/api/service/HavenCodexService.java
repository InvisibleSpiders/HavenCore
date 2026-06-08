package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.model.CodexCategory;
import dev.invisiblespiders.haven.api.model.CodexEntry;
import dev.invisiblespiders.haven.api.model.PlayerCodex;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HavenCodexService {

    /** Registers a codex entry. Call from your plugin's onEnable. */
    void registerEntry(CodexEntry entry);

    Optional<CodexEntry> getEntry(String key);

    Collection<CodexEntry> getEntries(CodexCategory category);

    Collection<CodexEntry> getAllEntries();

    CompletableFuture<PlayerCodex> getCodex(UUID playerUuid);

    /**
     * Records a discovery. Fires HavenCodexDiscoveryEvent and
     * HavenCodexMilestoneEvent if a milestone is reached.
     * Returns true if this was a new discovery.
     */
    CompletableFuture<Boolean> recordDiscovery(UUID playerUuid, String entryKey);

    CompletableFuture<Integer> getDiscoveryCount(UUID playerUuid, CodexCategory category);
}
