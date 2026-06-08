package dev.invisiblespiders.haven.api.event;

public abstract class HavenEvent {
    private final long timestamp = System.currentTimeMillis();
    public long getTimestamp() { return timestamp; }
}
