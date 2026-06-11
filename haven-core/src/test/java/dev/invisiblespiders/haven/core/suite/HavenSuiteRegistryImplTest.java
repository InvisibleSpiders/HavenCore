package dev.invisiblespiders.haven.core.suite;

import dev.invisiblespiders.haven.api.model.ReloadResult;
import dev.invisiblespiders.haven.api.model.SuiteHealthReport;
import dev.invisiblespiders.haven.api.service.HavenSuiteEntry;
import dev.invisiblespiders.haven.api.service.HavenSuiteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HavenSuiteRegistryImplTest {

    private HavenSuiteRegistry registry;

    @BeforeEach
    void setUp() { registry = new HavenSuiteRegistryImpl(); }

    @Test
    void registeredEntryAppearsInGetAll() {
        registry.register(entry("Alpha", "1.0"));
        assertEquals(1, registry.getAll().size());
        assertEquals("Alpha", registry.getAll().iterator().next().pluginName());
    }

    @Test
    void getByNameReturnsRegisteredEntry() {
        registry.register(entry("Beta", "2.0"));
        assertTrue(registry.get("Beta").isPresent());
        assertEquals("2.0", registry.get("Beta").get().pluginVersion());
    }

    @Test
    void getByNameReturnsEmptyForUnknown() {
        assertTrue(registry.get("Unknown").isEmpty());
    }

    @Test
    void unregisterRemovesEntry() {
        registry.register(entry("Gamma", "3.0"));
        registry.unregister("Gamma");
        assertTrue(registry.get("Gamma").isEmpty());
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void registeringTwiceOverwritesPrevious() {
        registry.register(entry("Delta", "1.0"));
        registry.register(entry("Delta", "2.0"));
        assertEquals(1, registry.getAll().size());
        assertEquals("2.0", registry.get("Delta").get().pluginVersion());
    }

    private static HavenSuiteEntry entry(String name, String version) {
        return new HavenSuiteEntry() {
            public String pluginName()    { return name; }
            public String pluginVersion() { return version; }
            public List<SuiteHealthReport> health() { return List.of(); }
            public ReloadResult reload()  { return ReloadResult.ok("ok"); }
        };
    }
}
