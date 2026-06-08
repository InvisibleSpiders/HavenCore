package dev.invisiblespiders.haven.api.event;

import dev.invisiblespiders.haven.api.hook.HavenHook;

public class HookRegisteredEvent extends HavenEvent {
    private final HavenHook hook;
    public HookRegisteredEvent(HavenHook hook) { this.hook = hook; }
    public HavenHook getHook() { return hook; }
}
