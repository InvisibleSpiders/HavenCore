package dev.invisiblespiders.haven.api.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extensible codex category registry.
 * Other plugins register new categories via CodexCategory.register("myid").
 */
public final class CodexCategory {

    public static final CodexCategory MOBS   = register("mobs");
    public static final CodexCategory ITEMS  = register("items");
    public static final CodexCategory ORES   = register("ores");
    public static final CodexCategory FISH   = register("fish");
    public static final CodexCategory BLOCKS = register("blocks");

    private static final Map<String, CodexCategory> REGISTRY = new LinkedHashMap<>();

    private final String id;

    private CodexCategory(String id) { this.id = id; }

    public static synchronized CodexCategory register(String id) {
        return REGISTRY.computeIfAbsent(id.toLowerCase(), CodexCategory::new);
    }

    public static synchronized Collection<CodexCategory> values() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static synchronized CodexCategory of(String id) {
        CodexCategory cat = REGISTRY.get(id.toLowerCase());
        if (cat == null) throw new IllegalArgumentException("Unknown CodexCategory: " + id);
        return cat;
    }

    public String getId() { return id; }

    @Override public String toString() { return id; }

    @Override public boolean equals(Object o) {
        return this == o || (o instanceof CodexCategory c && id.equals(c.id));
    }

    @Override public int hashCode() { return id.hashCode(); }
}
