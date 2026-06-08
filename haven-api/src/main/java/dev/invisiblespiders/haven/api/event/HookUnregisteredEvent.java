package dev.invisiblespiders.haven.api.event;

import dev.invisiblespiders.haven.api.hook.HavenHook;

public class HookUnregisteredEvent extends HavenEvent {
    private final HavenHook hook;
    public HookUnregisteredEvent(HavenHook hook) { this.hook = hook; }
    public HavenHook getHook() { return hook; }
}
