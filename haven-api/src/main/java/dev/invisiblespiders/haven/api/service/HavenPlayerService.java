package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.model.HavenPlayer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HavenPlayerService {

    /** Returns cached profile if online, else loads from DB asynchronously. */
    CompletableFuture<Optional<HavenPlayer>> get(UUID uuid);

    /** Lookup by last-known name (case-insensitive). */
    CompletableFuture<Optional<HavenPlayer>> get(String name);

    /** Returns all currently online players' cached profiles. */
    Collection<HavenPlayer> getOnline();

    /** Returns cached profile for online player, or empty if not cached. */
    Optional<HavenPlayer> getCached(UUID uuid);

    /** Persists the player's data map changes to the DB. */
    CompletableFuture<Void> save(HavenPlayer player);
}
