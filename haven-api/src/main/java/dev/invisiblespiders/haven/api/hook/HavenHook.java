package dev.invisiblespiders.haven.api.hook;

public interface HavenHook {

    /** Stable, unique identifier for this hook, e.g. "vaultunlocked". */
    String getId();

    /** Returns true if the target plugin is present and usable. */
    boolean isAvailable();

    /** Called when the hook is registered. Connect to the target plugin here. */
    void onEnable();

    /** Called on HavenCore disable. Release any references here. */
    void onDisable();
}
