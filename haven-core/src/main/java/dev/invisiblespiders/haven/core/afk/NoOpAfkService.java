package dev.invisiblespiders.haven.core.afk;

import dev.invisiblespiders.haven.api.service.HavenAfkService;
import java.util.UUID;

public final class NoOpAfkService implements HavenAfkService {
    @Override public boolean isAfk(UUID uuid) { return false; }
    @Override public long getIdleSeconds(UUID uuid) { return 0L; }
    @Override public void setAfk(UUID uuid, boolean afk) {}
}
