# Sleep Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a sleep-to-skip-night module that fast-forwards time tick-by-tick based on how many active (non-AFK, non-spectator, non-bypass) players are sleeping in a world.

**Architecture:** `SleepManager implements HavenSleepService + Listener` owns all state. A single `BukkitRunnable` ticks every server-tick advancing world time during SKIPPING state; it starts/stops as worlds transition in and out of SKIPPING. Per-world state machine (IDLE → WAITING → SKIPPING ↔ PAUSED → IDLE) re-evaluates on every bed enter/leave/quit event.

**Tech Stack:** Paper API (PlayerBedEnterEvent, World.setSleepingIgnored, Player.wakeup), Mockito + AssertJ for tests, MiniMessage for messages, YamlConfiguration for config.

---

## File Map

**New — haven-api:**
- `haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenSleepService.java`
- `haven-api/src/main/java/dev/invisiblespiders/haven/api/event/HavenSleepSkipStartEvent.java`
- `haven-api/src/main/java/dev/invisiblespiders/haven/api/event/HavenSleepSkipCompleteEvent.java`
- `haven-api/src/test/java/dev/invisiblespiders/haven/api/sleep/SleepApiModelTest.java`

**New — haven-core:**
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepSettings.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepManager.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepAdminCommand.java`
- `haven-core/src/main/resources/sleep.yml`
- `haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepSettingsTest.java`
- `haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepManagerTest.java`
- `haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepAdminCommandTest.java`

**Modified — haven-core:**
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/config/ConfigManager.java` — add `sleep` field + `getSleep()`
- `haven-core/src/main/resources/config.yml` — add `features.sleep: true`
- `haven-core/src/main/resources/plugin.yml` — add `haven.sleep.bypass` + `haven.admin.sleep` permissions
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java` — wire SleepManager
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/command/HavenCommand.java` — add `sleep` subcommand
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/diagnostic/CoreDiagnostics.java` — add sleep service check

---

## Task 1: haven-api — HavenSleepService + Events

**Files:**
- Create: `haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenSleepService.java`
- Create: `haven-api/src/main/java/dev/invisiblespiders/haven/api/event/HavenSleepSkipStartEvent.java`
- Create: `haven-api/src/main/java/dev/invisiblespiders/haven/api/event/HavenSleepSkipCompleteEvent.java`
- Test: `haven-api/src/test/java/dev/invisiblespiders/haven/api/sleep/SleepApiModelTest.java`

- [ ] **Step 1: Create HavenSleepService.java**

```java
package dev.invisiblespiders.haven.api.service;

import org.bukkit.World;

public interface HavenSleepService {
    /** True only while time is actively advancing (not while paused). */
    boolean isSkipping(World world);
    /** Number of active players currently in beds in this world. */
    int getSleepingCount(World world);
    /** Non-AFK, non-spectator, non-bypass players currently in this world. */
    int getActiveCount(World world);
}
```

- [ ] **Step 2: Create HavenSleepSkipStartEvent.java**

```java
package dev.invisiblespiders.haven.api.event;

import org.bukkit.World;

public class HavenSleepSkipStartEvent extends HavenEvent {
    private final World world;
    private final int sleeping;
    private final int active;

    public HavenSleepSkipStartEvent(World world, int sleeping, int active) {
        this.world = world;
        this.sleeping = sleeping;
        this.active = active;
    }

    public World getWorld()   { return world; }
    public int getSleeping()  { return sleeping; }
    public int getActive()    { return active; }
}
```

- [ ] **Step 3: Create HavenSleepSkipCompleteEvent.java**

```java
package dev.invisiblespiders.haven.api.event;

import org.bukkit.World;

public class HavenSleepSkipCompleteEvent extends HavenEvent {
    private final World world;

    public HavenSleepSkipCompleteEvent(World world) {
        this.world = world;
    }

    public World getWorld() { return world; }
}
```

- [ ] **Step 4: Write and run the test**

```java
package dev.invisiblespiders.haven.api.sleep;

import dev.invisiblespiders.haven.api.event.HavenSleepSkipCompleteEvent;
import dev.invisiblespiders.haven.api.event.HavenSleepSkipStartEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SleepApiModelTest {

    @Test
    void skipStartEventExposesAllFields() {
        HavenSleepSkipStartEvent event = new HavenSleepSkipStartEvent(null, 2, 5);
        assertNull(event.getWorld());
        assertEquals(2, event.getSleeping());
        assertEquals(5, event.getActive());
        assertTrue(event.getTimestamp() > 0);
    }

    @Test
    void skipCompleteEventExposesWorld() {
        HavenSleepSkipCompleteEvent event = new HavenSleepSkipCompleteEvent(null);
        assertNull(event.getWorld());
        assertTrue(event.getTimestamp() > 0);
    }
}
```

Run: `./gradlew :haven-api:test --tests "*.SleepApiModelTest" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenSleepService.java \
        haven-api/src/main/java/dev/invisiblespiders/haven/api/event/HavenSleepSkipStartEvent.java \
        haven-api/src/main/java/dev/invisiblespiders/haven/api/event/HavenSleepSkipCompleteEvent.java \
        haven-api/src/test/java/dev/invisiblespiders/haven/api/sleep/SleepApiModelTest.java
git commit -m "feat(api): add HavenSleepService interface and skip events"
```

---

## Task 2: SleepSettings + sleep.yml

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepSettings.java`
- Create: `haven-core/src/main/resources/sleep.yml`
- Test: `haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepSettingsTest.java`

- [ ] **Step 1: Write failing test**

```java
package dev.invisiblespiders.haven.core.sleep;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SleepSettingsTest {

    @Test
    void defaultsFromEmptyConfig() {
        SleepSettings s = SleepSettings.from(new YamlConfiguration());
        assertThat(s.worlds()).containsExactly("world");
        assertThat(s.minCount()).isEqualTo(1);
        assertThat(s.minPercent()).isEqualTo(0);
        assertThat(s.minSpeed()).isEqualTo(40L);
        assertThat(s.maxSpeed()).isEqualTo(200L);
    }

    @Test
    void readsConfiguredValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("worlds", List.of("world", "world_the_end"));
        config.set("threshold.min-count", 2);
        config.set("threshold.min-percent", 50);
        config.set("speed.min", 20);
        config.set("speed.max", 100);

        SleepSettings s = SleepSettings.from(config);

        assertThat(s.worlds()).containsExactly("world", "world_the_end");
        assertThat(s.minCount()).isEqualTo(2);
        assertThat(s.minPercent()).isEqualTo(50);
        assertThat(s.minSpeed()).isEqualTo(20L);
        assertThat(s.maxSpeed()).isEqualTo(100L);
    }

    @Test
    void worldsListIsImmutable() {
        SleepSettings s = SleepSettings.from(new YamlConfiguration());
        assertThrows(UnsupportedOperationException.class, () -> s.worlds().add("extra"));
    }

    @Test
    void messagesHaveNonBlankDefaults() {
        SleepSettings s = SleepSettings.from(new YamlConfiguration());
        assertThat(s.messages().sleeping()).isNotBlank();
        assertThat(s.messages().skipStart()).isNotBlank();
        assertThat(s.messages().skipPaused()).isNotBlank();
        assertThat(s.messages().skipComplete()).isNotBlank();
        assertThat(s.messages().broadcastSkip()).isNotBlank();
    }
}
```

Run: `./gradlew :haven-core:test --tests "*.SleepSettingsTest" -i`
Expected: FAIL — `SleepSettings` does not exist yet.

- [ ] **Step 2: Create SleepSettings.java**

```java
package dev.invisiblespiders.haven.core.sleep;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record SleepSettings(
    List<String> worlds,
    int minCount,
    int minPercent,
    long minSpeed,
    long maxSpeed,
    SleepMessages messages
) {
    public SleepSettings {
        worlds = List.copyOf(worlds);
    }

    public static SleepSettings from(FileConfiguration config) {
        List<String> worlds = config.getStringList("worlds");
        if (worlds.isEmpty()) worlds = List.of("world");
        return new SleepSettings(
            worlds,
            config.getInt("threshold.min-count", 1),
            config.getInt("threshold.min-percent", 0),
            config.getLong("speed.min", 40L),
            config.getLong("speed.max", 200L),
            SleepMessages.from(config)
        );
    }

    public record SleepMessages(
        String sleeping,
        String skipStart,
        String skipPaused,
        String skipComplete,
        String broadcastSkip
    ) {
        public static SleepMessages from(FileConfiguration config) {
            return new SleepMessages(
                config.getString("messages.sleeping",     "<gray>☽ <white><sleeping></white>/<active> sleeping..."),
                config.getString("messages.skip-start",   "<gold>☽ Night is being skipped!"),
                config.getString("messages.skip-paused",  "<gray>☽ Skip paused — <white><sleeping></white>/<active> sleeping"),
                config.getString("messages.skip-complete","<green>☀ Dawn breaks!"),
                config.getString("messages.broadcast-skip","<gold>☀ <gray>The night has passed.")
            );
        }
    }
}
```

- [ ] **Step 3: Create sleep.yml**

```yaml
# HavenCore Sleep Configuration
# Changes require restart (world.setSleepingIgnored is applied on enable).

worlds:
  - world          # Add additional world names here

threshold:
  min-count: 1     # Minimum number of sleeping players to start skip (0 = disabled)
  min-percent: 0   # Minimum % of active players sleeping (0 = disabled)
                   # Both conditions must be met. Default: any 1 player starts the skip.

speed:
  min: 40          # Game-ticks advanced per server-tick at lowest sleeper ratio (~12s skip)
  max: 200         # Game-ticks advanced per server-tick when 100% of active players sleep (~2.5s skip)
                   # Normal day/night cycle = 1 game-tick per server-tick.

messages:
  sleeping:         "<gray>☽ <white><sleeping></white>/<active> sleeping..."
  skip-start:       "<gold>☽ Night is being skipped!"
  skip-paused:      "<gray>☽ Skip paused — <white><sleeping></white>/<active> sleeping"
  skip-complete:    "<green>☀ Dawn breaks!"
  broadcast-skip:   "<gold>☀ <gray>The night has passed."
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :haven-core:test --tests "*.SleepSettingsTest" -i`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepSettings.java \
        haven-core/src/main/resources/sleep.yml \
        haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepSettingsTest.java
git commit -m "feat(sleep): add SleepSettings and sleep.yml config"
```

---

## Task 3: ConfigManager + config.yml + plugin.yml

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/config/ConfigManager.java`
- Modify: `haven-core/src/main/resources/config.yml`
- Modify: `haven-core/src/main/resources/plugin.yml`

- [ ] **Step 1: Add `sleep` to ConfigManager**

In `ConfigManager.java`:

1. Add field alongside the other config fields:
```java
private FileConfiguration sleep;
```

2. Add `"sleep.yml"` to `CONFIG_FILES`:
```java
private static final List<String> CONFIG_FILES = Arrays.asList(
    "config.yml", "database.yml", "messages.yml",
    "economy.yml", "storage.yml", "codex.yml", "hooks.yml", "op-toggle.yml",
    "upgrades.yml", "rewards.yml", "afk.yml", "tab.yml", "chat.yml",
    "custommessages.yml", "sleep.yml"           // ← add sleep.yml here
);
```

3. Add to `reload()` alongside other loads:
```java
sleep    = load("sleep.yml");
```

4. Add getter:
```java
public FileConfiguration getSleep() { return sleep; }
```

- [ ] **Step 2: Add `features.sleep` to config.yml**

In `config.yml`, add `sleep: true` to the `features` block:
```yaml
features:
  player-profiles: true
  economy: true
  virtual-storage: true
  codex: true
  notifications: true
  cooldowns: true
  tiers: true
  item-registry: true
  hooks: true
  afk: true
  tab-list: true
  chat-formatting: true
  custom-messages: true
  sleep: true          # ← add this line
```

- [ ] **Step 3: Add permissions to plugin.yml**

Add to the `permissions` block:
```yaml
  haven.sleep.bypass:
    description: Excluded from active player count and sleep threshold checks
    default: false
  haven.admin.sleep:
    description: Manage sleep module via /haven sleep skip|toggle|status
    default: op
```

Add `haven.admin.sleep: true` as a child of `haven.admin`:
```yaml
  haven.admin:
    description: Access to all /haven admin subcommands
    default: op
    children:
      haven.admin.reload: true
      haven.admin.doctor: true
      haven.admin.codex: true
      haven.admin.upgrades: true
      haven.admin.rewards: true
      haven.admin.sleep: true     # ← add this line
```

- [ ] **Step 4: Build to confirm no compilation errors**

Run: `./gradlew :haven-core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/config/ConfigManager.java \
        haven-core/src/main/resources/config.yml \
        haven-core/src/main/resources/plugin.yml
git commit -m "feat(sleep): add sleep config, feature toggle, and permissions"
```

---

## Task 4: SleepManager — skeleton + active player counting

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepManager.java`
- Test: `haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepManagerTest.java`

- [ ] **Step 1: Write failing tests for active player counting and speed**

```java
package dev.invisiblespiders.haven.core.sleep;

import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SleepManagerTest {

    @Mock Plugin plugin;
    @Mock Server server;
    @Mock HavenAfkService afkService;
    @Mock HavenEventBus eventBus;
    @Mock Logger logger;
    @Mock World world;
    @Mock Player player1;
    @Mock Player player2;

    SleepSettings settings;
    SleepManager manager;

    @BeforeEach
    void setup() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(world.getName()).thenReturn("world");
        settings = SleepSettings.from(new YamlConfiguration()); // min-count=1, min-percent=0, min=40, max=200
        manager = new SleepManager(settings, afkService, eventBus, plugin, logger);
    }

    // ── Active count ──────────────────────────────────────────────────────────

    @Test
    void countActive_excludesSpectators() {
        when(world.getPlayers()).thenReturn(List.of(player1));
        when(player1.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertThat(manager.countActive(world)).isZero();
    }

    @Test
    void countActive_excludesBypassPermission() {
        when(world.getPlayers()).thenReturn(List.of(player1));
        when(player1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player1.hasPermission("haven.sleep.bypass")).thenReturn(true);
        assertThat(manager.countActive(world)).isZero();
    }

    @Test
    void countActive_excludesAfkPlayers() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        when(player1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player1.hasPermission("haven.sleep.bypass")).thenReturn(false);
        when(player1.getUniqueId()).thenReturn(uuid);
        when(afkService.isAfk(uuid)).thenReturn(true);
        assertThat(manager.countActive(world)).isZero();
    }

    @Test
    void countActive_countsEligiblePlayers() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);
        assertThat(manager.countActive(world)).isEqualTo(2);
    }

    @Test
    void countActive_worksWithNullAfkService() {
        SleepManager noAfk = new SleepManager(settings, null, eventBus, plugin, logger);
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        when(player1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player1.hasPermission("haven.sleep.bypass")).thenReturn(false);
        when(player1.getUniqueId()).thenReturn(uuid);
        assertThat(noAfk.countActive(world)).isEqualTo(1);
    }

    // ── Speed ─────────────────────────────────────────────────────────────────

    @Test
    void computeSpeed_returnsMinWhenNoSleepers() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        // 0 sleeping / 1 active → ratio 0 → minSpeed
        assertThat(manager.computeSpeed(world)).isEqualTo(40L);
    }

    @Test
    void computeSpeed_returnsMaxWhenAllSleep() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        manager.sleepingPlayers.get("world").add(uuid); // 1/1 = 100%
        assertThat(manager.computeSpeed(world)).isEqualTo(200L);
    }

    @Test
    void computeSpeed_interpolatesMidpoint() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);
        manager.sleepingPlayers.get("world").add(uuid1); // 1/2 = 50%
        // 40 + round((200-40) * 0.5) = 40 + 80 = 120
        assertThat(manager.computeSpeed(world)).isEqualTo(120L);
    }

    // ── Threshold ─────────────────────────────────────────────────────────────

    @Test
    void meetsThreshold_falseWhenNoOneSleeping() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        assertThat(manager.meetsThreshold(world)).isFalse();
    }

    @Test
    void meetsThreshold_trueWhenMinCountOneSleeping() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        manager.sleepingPlayers.get("world").add(uuid);
        assertThat(manager.meetsThreshold(world)).isTrue();
    }

    @Test
    void meetsThreshold_respectsMinPercent() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("threshold.min-count", 0);
        config.set("threshold.min-percent", 50);
        SleepManager strictManager = new SleepManager(
            SleepSettings.from(config), afkService, eventBus, plugin, logger);

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);

        // 1 sleeping / 2 active = 50% — exactly meets 50% threshold
        strictManager.sleepingPlayers.get("world").add(uuid1);
        assertThat(strictManager.meetsThreshold(world)).isTrue();
    }

    @Test
    void meetsThreshold_falseWhenBelowMinPercent() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("threshold.min-count", 0);
        config.set("threshold.min-percent", 75);
        SleepManager strictManager = new SleepManager(
            SleepSettings.from(config), afkService, eventBus, plugin, logger);

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);

        // 1/2 = 50%, below 75%
        strictManager.sleepingPlayers.get("world").add(uuid1);
        assertThat(strictManager.meetsThreshold(world)).isFalse();
    }

    // ── Eligibility ───────────────────────────────────────────────────────────

    @Test
    void isEligible_trueForConfiguredWorld() {
        assertThat(manager.isEligible(world)).isTrue();
    }

    @Test
    void isEligible_falseForUnknownWorld() {
        World other = mock(World.class);
        when(other.getName()).thenReturn("world_nether");
        assertThat(manager.isEligible(other)).isFalse();
    }

    // Helper

    private void setupSurvivalPlayer(Player player, UUID uuid, boolean afk) {
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.hasPermission("haven.sleep.bypass")).thenReturn(false);
        when(player.getUniqueId()).thenReturn(uuid);
        when(afkService.isAfk(uuid)).thenReturn(afk);
    }
}
```

Run: `./gradlew :haven-core:test --tests "*.SleepManagerTest" -i`
Expected: FAIL — `SleepManager` does not exist yet.

- [ ] **Step 2: Create SleepManager.java (skeleton — counting only)**

```java
package dev.invisiblespiders.haven.core.sleep;

import dev.invisiblespiders.haven.api.event.HavenSleepSkipCompleteEvent;
import dev.invisiblespiders.haven.api.event.HavenSleepSkipStartEvent;
import dev.invisiblespiders.haven.api.service.HavenAfkService;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.api.service.HavenSleepService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SleepManager implements HavenSleepService, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long DAWN_TICK = 23460L;

    enum State { IDLE, WAITING, SKIPPING, PAUSED }

    private final SleepSettings settings;
    private final @Nullable HavenAfkService afkService;
    private final HavenEventBus eventBus;
    private final Plugin plugin;
    private final Logger logger;

    // Keyed by world name for safe map lookup without holding World references.
    final Map<String, Set<UUID>> sleepingPlayers = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> skippedWith = new ConcurrentHashMap<>();
    private final Map<String, State> worldStates = new ConcurrentHashMap<>();
    private final Map<String, Boolean> runtimeEnabled = new ConcurrentHashMap<>();

    private @Nullable BukkitTask tickTask;

    public SleepManager(SleepSettings settings, @Nullable HavenAfkService afkService,
                        HavenEventBus eventBus, Plugin plugin, Logger logger) {
        this.settings = settings;
        this.afkService = afkService;
        this.eventBus = eventBus;
        this.plugin = plugin;
        this.logger = logger;
        for (String name : settings.worlds()) {
            worldStates.put(name, State.IDLE);
            sleepingPlayers.put(name, ConcurrentHashMap.newKeySet());
            runtimeEnabled.put(name, Boolean.TRUE);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void onEnable() {
        for (String name : settings.worlds()) {
            World world = Bukkit.getWorld(name);
            if (world != null) world.setSleepingIgnored(true);
        }
    }

    public void onDisable() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        for (String name : settings.worlds()) {
            World world = Bukkit.getWorld(name);
            if (world != null) world.setSleepingIgnored(false);
        }
    }

    // ── HavenSleepService ─────────────────────────────────────────────────────

    @Override
    public boolean isSkipping(World world) {
        return worldStates.getOrDefault(world.getName(), State.IDLE) == State.SKIPPING;
    }

    @Override
    public int getSleepingCount(World world) {
        return sleepingPlayers.getOrDefault(world.getName(), Set.of()).size();
    }

    @Override
    public int getActiveCount(World world) {
        return countActive(world);
    }

    // ── Internal counting ─────────────────────────────────────────────────────

    boolean isEligible(World world) {
        return settings.worlds().contains(world.getName())
            && Boolean.TRUE.equals(runtimeEnabled.get(world.getName()));
    }

    int countActive(World world) {
        return (int) world.getPlayers().stream()
            .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
            .filter(p -> !p.hasPermission("haven.sleep.bypass"))
            .filter(p -> afkService == null || !afkService.isAfk(p.getUniqueId()))
            .count();
    }

    boolean meetsThreshold(World world) {
        int sleeping = getSleepingCount(world);
        if (sleeping == 0) return false;
        int active = countActive(world);
        if (active == 0) return false;
        boolean countOk  = settings.minCount() <= 0  || sleeping >= settings.minCount();
        boolean percentOk = settings.minPercent() <= 0
            || sleeping >= (int) Math.ceil(active * settings.minPercent() / 100.0);
        return countOk && percentOk;
    }

    long computeSpeed(World world) {
        int sleeping = getSleepingCount(world);
        int active   = countActive(world);
        double ratio = active > 0 ? Math.max(0.0, Math.min(1.0, (double) sleeping / active)) : 1.0;
        return settings.minSpeed() + Math.round((settings.maxSpeed() - settings.minSpeed()) * ratio);
    }

    State getState(World world) {
        return worldStates.getOrDefault(world.getName(), State.IDLE);
    }

    SleepSettings getSettings() { return settings; }
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :haven-core:test --tests "*.SleepManagerTest" -i`
Expected: PASS (all counting + threshold + speed tests)

- [ ] **Step 4: Commit**

```bash
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepManager.java \
        haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepManagerTest.java
git commit -m "feat(sleep): add SleepManager skeleton with active counting and threshold logic"
```

---

## Task 5: SleepManager — state machine + bed events

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepManager.java`
- Modify: `haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepManagerTest.java`

- [ ] **Step 1: Add state machine tests to SleepManagerTest.java**

Add these test methods inside `SleepManagerTest`:

```java
    // ── State machine ─────────────────────────────────────────────────────────

    @Test
    void reevaluate_transitionsIdleToWaitingWhenSomeoneSleeepsButBelowThreshold() {
        // With min-count=1 any single sleeper meets threshold, so test with min-count=2
        YamlConfiguration config = new YamlConfiguration();
        config.set("threshold.min-count", 2);
        SleepManager m = new SleepManager(SleepSettings.from(config), afkService, eventBus, plugin, logger);

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        setupSurvivalPlayer(player1, uuid1, false);
        setupSurvivalPlayer(player2, uuid2, false);

        m.sleepingPlayers.get("world").add(uuid1); // 1 sleeping, min-count=2 → WAITING
        m.reevaluate(world);

        assertThat(m.getState(world)).isEqualTo(SleepManager.State.WAITING);
    }

    @Test
    void reevaluate_transitionsWaitingToIdleWhenNoOneSleeping() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("threshold.min-count", 2);
        SleepManager m = new SleepManager(SleepSettings.from(config), afkService, eventBus, plugin, logger);

        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, UUID.randomUUID(), false);

        // Nothing sleeping → IDLE
        m.reevaluate(world);
        assertThat(m.getState(world)).isEqualTo(SleepManager.State.IDLE);
    }

    @Test
    void reevaluate_publishesStartEventWhenThresholdFirstMet() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        when(world.getTime()).thenReturn(13000L); // night

        manager.sleepingPlayers.get("world").add(uuid); // 1/1 = meets min-count=1
        manager.reevaluate(world);

        verify(eventBus).publish(any(HavenSleepSkipStartEvent.class));
        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.SKIPPING);
    }

    @Test
    void reevaluate_transitionsSkippingToPausedWhenThresholdDrops() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        when(world.getTime()).thenReturn(13000L);

        // Enter skipping
        manager.sleepingPlayers.get("world").add(uuid);
        manager.reevaluate(world);
        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.SKIPPING);

        // Player wakes — below threshold
        manager.sleepingPlayers.get("world").remove(uuid);
        manager.reevaluate(world);
        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.PAUSED);
    }

    @Test
    void reevaluate_resumesFromPausedWhenThresholdMetAgain() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        when(world.getTime()).thenReturn(13000L);

        // Skip → Pause → Resume
        manager.sleepingPlayers.get("world").add(uuid);
        manager.reevaluate(world); // SKIPPING
        manager.sleepingPlayers.get("world").remove(uuid);
        manager.reevaluate(world); // PAUSED
        manager.sleepingPlayers.get("world").add(uuid);
        manager.reevaluate(world); // back to SKIPPING

        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.SKIPPING);
    }
```

Run: `./gradlew :haven-core:test --tests "*.SleepManagerTest" -i`
Expected: FAIL — `reevaluate()` and `getState()` missing.

- [ ] **Step 2: Add state machine methods to SleepManager.java**

Add inside `SleepManager`, after `getSettings()`:

```java
    // ── State machine ─────────────────────────────────────────────────────────

    void reevaluate(World world) {
        State current = worldStates.getOrDefault(world.getName(), State.IDLE);
        boolean threshold = meetsThreshold(world);
        int sleeping = getSleepingCount(world);

        switch (current) {
            case IDLE, WAITING -> {
                if (threshold) {
                    transitionToSkipping(world);
                } else {
                    worldStates.put(world.getName(), sleeping > 0 ? State.WAITING : State.IDLE);
                    sendActionBarToWorld(world);
                }
            }
            case SKIPPING -> {
                if (!threshold) transitionToPaused(world);
                // else BukkitRunnable continues ticking
            }
            case PAUSED -> {
                if (threshold) {
                    transitionToSkipping(world);
                } else {
                    sendActionBarToWorld(world);
                }
            }
        }
    }

    private void transitionToSkipping(World world) {
        State previous = worldStates.put(world.getName(), State.SKIPPING);
        if (previous == State.SKIPPING) return; // already skipping, no-op
        int sleeping = getSleepingCount(world);
        int active   = countActive(world);
        skippedWith.computeIfAbsent(world.getName(), k -> ConcurrentHashMap.newKeySet())
            .addAll(sleepingPlayers.getOrDefault(world.getName(), Set.of()));
        eventBus.publish(new HavenSleepSkipStartEvent(world, sleeping, active));
        broadcastToWorld(world, settings.messages().skipStart());
        ensureTickRunning();
    }

    private void transitionToPaused(World world) {
        worldStates.put(world.getName(), State.PAUSED);
        sendActionBarToWorld(world);
        stopTickIfNotNeeded();
    }

    private void ensureTickRunning() {
        if (tickTask != null && !tickTask.isCancelled()) return;
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (String name : settings.worlds()) {
                    if (worldStates.getOrDefault(name, State.IDLE) != State.SKIPPING) continue;
                    World w = Bukkit.getWorld(name);
                    if (w != null) tickWorld(w);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void stopTickIfNotNeeded() {
        boolean anySkipping = worldStates.values().stream().anyMatch(s -> s == State.SKIPPING);
        if (!anySkipping && tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    // ── Placeholder stubs (filled in Task 6–7) ────────────────────────────────

    private void tickWorld(World world) { /* Task 6 */ }

    void completeSkip(World world) { /* Task 6 */ }

    private void sendActionBarToWorld(World world) { /* Task 7 */ }

    private void broadcastToWorld(World world, String miniMessage) { /* Task 7 */ }
```

- [ ] **Step 3: Add bed event handlers to SleepManager.java**

Add after `broadcastToWorld`:

```java
    // ── Bukkit event handlers ─────────────────────────────────────────────────

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBedEnter(org.bukkit.event.player.PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player player = event.getPlayer();
        World world   = player.getWorld();
        if (!isEligible(world)) return;
        long time = world.getTime();
        if (time < 12541L || time >= DAWN_TICK) return; // not night

        sleepingPlayers.get(world.getName()).add(player.getUniqueId());
        reevaluate(world);
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onBedLeave(org.bukkit.event.player.PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        World world   = player.getWorld();
        if (!isEligible(world)) return;

        sleepingPlayers.getOrDefault(world.getName(), Set.of()).remove(player.getUniqueId());
        reevaluate(world);
    }

    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        World world   = player.getWorld();
        if (!isEligible(world)) return;

        sleepingPlayers.getOrDefault(world.getName(), Set.of()).remove(player.getUniqueId());
        reevaluate(world);
    }
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :haven-core:test --tests "*.SleepManagerTest" -i`
Expected: PASS (all counting + state machine tests; tickWorld/sendActionBar stubs do nothing but don't throw)

- [ ] **Step 5: Commit**

```bash
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepManager.java \
        haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepManagerTest.java
git commit -m "feat(sleep): add state machine, threshold transitions, and bed event handlers"
```

---

## Task 6: SleepManager — time advancer + skip complete

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepManager.java`
- Modify: `haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepManagerTest.java`

- [ ] **Step 1: Add skip-complete tests**

Add to `SleepManagerTest`:

```java
    // ── Skip completion ───────────────────────────────────────────────────────

    @Test
    void completeSkip_setsTimeTo0AndTransitionsToIdle() {
        when(world.getPlayers()).thenReturn(List.of());
        manager.worldStates.put("world", SleepManager.State.SKIPPING);

        manager.completeSkip(world);

        verify(world).setTime(0L);
        assertThat(manager.getState(world)).isEqualTo(SleepManager.State.IDLE);
    }

    @Test
    void completeSkip_resetsInsomniaOnlyForSleepers() {
        UUID sleptUuid  = UUID.randomUUID();
        UUID awakeUuid  = UUID.randomUUID();

        when(player1.getUniqueId()).thenReturn(sleptUuid);
        when(player2.getUniqueId()).thenReturn(awakeUuid);
        when(player1.isSleeping()).thenReturn(false);
        when(player2.isSleeping()).thenReturn(false);
        when(world.getPlayers()).thenReturn(List.of(player1, player2));

        manager.worldStates.put("world", SleepManager.State.SKIPPING);
        manager.skippedWith.put("world", new java.util.HashSet<>(Set.of(sleptUuid)));

        manager.completeSkip(world);

        verify(player1).setStatistic(org.bukkit.Statistic.TIME_SINCE_REST, 0);
        verify(player2, never()).setStatistic(any(), anyInt());
    }

    @Test
    void completeSkip_ejectsPlayersStillInBed() {
        when(world.getPlayers()).thenReturn(List.of(player1, player2));
        when(player1.isSleeping()).thenReturn(true);
        when(player2.isSleeping()).thenReturn(false);

        manager.worldStates.put("world", SleepManager.State.SKIPPING);

        manager.completeSkip(world);

        verify(player1).wakeup(false);
        verify(player2, never()).wakeup(anyBoolean());
    }

    @Test
    void completeSkip_publishesCompleteEvent() {
        when(world.getPlayers()).thenReturn(List.of());
        manager.worldStates.put("world", SleepManager.State.SKIPPING);

        manager.completeSkip(world);

        verify(eventBus).publish(any(HavenSleepSkipCompleteEvent.class));
    }

    @Test
    void completeSkip_clearsSleepingPlayersSet() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of());
        manager.worldStates.put("world", SleepManager.State.SKIPPING);
        manager.sleepingPlayers.get("world").add(uuid);

        manager.completeSkip(world);

        assertThat(manager.sleepingPlayers.get("world")).isEmpty();
    }
```

Run: `./gradlew :haven-core:test --tests "*.SleepManagerTest" -i`
Expected: FAIL — `completeSkip` is a stub.

- [ ] **Step 2: Implement `tickWorld` and `completeSkip` in SleepManager.java**

Replace the stub `tickWorld` and `completeSkip` methods:

```java
    private void tickWorld(World world) {
        long current = world.getTime();
        if (current < 12541L || current >= DAWN_TICK) {
            completeSkip(world);
            return;
        }
        long next = current + computeSpeed(world);
        if (next >= DAWN_TICK) {
            completeSkip(world);
        } else {
            world.setTime(next);
            sendActionBarToWorld(world);
        }
    }

    void completeSkip(World world) {
        world.setTime(0L);

        Set<UUID> slept = skippedWith.getOrDefault(world.getName(), Set.of());
        for (Player p : world.getPlayers()) {
            if (slept.contains(p.getUniqueId())) {
                p.setStatistic(org.bukkit.Statistic.TIME_SINCE_REST, 0);
            }
            if (p.isSleeping()) {
                p.wakeup(false);
            }
        }

        worldStates.put(world.getName(), State.IDLE);
        sleepingPlayers.getOrDefault(world.getName(), Set.of()).clear();
        skippedWith.remove(world.getName());
        stopTickIfNotNeeded();
        eventBus.publish(new HavenSleepSkipCompleteEvent(world));
        sendActionBarToWorld(world); // clears action bar (template is null for IDLE → no-op after Task 7)
        broadcastToWorld(world, settings.messages().skipComplete());
        broadcastToWorld(world, settings.messages().broadcastSkip());
    }
```

- [ ] **Step 3: Add `forceSkip` and `toggle` admin methods**

Add after `completeSkip`:

```java
    public void forceSkip(World world) {
        if (!isEligible(world)) return;
        long time = world.getTime();
        if (time < 12541L || time >= DAWN_TICK) return; // already day — nothing to skip
        skippedWith.computeIfAbsent(world.getName(), k -> ConcurrentHashMap.newKeySet())
            .addAll(sleepingPlayers.getOrDefault(world.getName(), Set.of()));
        worldStates.put(world.getName(), State.SKIPPING);
        int sleeping = getSleepingCount(world);
        int active   = countActive(world);
        eventBus.publish(new HavenSleepSkipStartEvent(world, sleeping, active));
        broadcastToWorld(world, settings.messages().skipStart());
        ensureTickRunning();
    }

    public boolean toggle(World world) {
        boolean current  = Boolean.TRUE.equals(runtimeEnabled.get(world.getName()));
        boolean newState = !current;
        runtimeEnabled.put(world.getName(), newState);
        if (!newState) {
            sleepingPlayers.getOrDefault(world.getName(), Set.of()).clear();
            worldStates.put(world.getName(), State.IDLE);
            stopTickIfNotNeeded();
        }
        return newState;
    }
```

- [ ] **Step 4: Run all sleep tests**

Run: `./gradlew :haven-core:test --tests "*.SleepManagerTest" -i`
Expected: PASS (all tests including skip-complete)

- [ ] **Step 5: Commit**

```bash
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepManager.java \
        haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepManagerTest.java
git commit -m "feat(sleep): implement time advancement, skip completion, insomnia reset, forceSkip, toggle"
```

---

## Task 7: SleepManager — player feedback (action bar + chat broadcast)

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepManager.java`
- Modify: `haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepManagerTest.java`

- [ ] **Step 1: Add feedback tests**

Add to `SleepManagerTest`:

```java
    // ── Player feedback ───────────────────────────────────────────────────────

    @Test
    void sendActionBar_sendsFormattedMessageToWorldPlayers() {
        UUID uuid = UUID.randomUUID();
        when(world.getPlayers()).thenReturn(List.of(player1));
        setupSurvivalPlayer(player1, uuid, false);
        manager.sleepingPlayers.get("world").add(uuid);
        manager.worldStates.put("world", SleepManager.State.WAITING);

        manager.sendActionBarToWorld(world);

        verify(player1).sendActionBar(any(Component.class));
    }

    @Test
    void sendActionBar_sendsNothingForIdleState() {
        when(world.getPlayers()).thenReturn(List.of(player1));
        manager.worldStates.put("world", SleepManager.State.IDLE);

        manager.sendActionBarToWorld(world);

        verify(player1, never()).sendActionBar(any(Component.class));
    }

    @Test
    void broadcastToWorld_sendsMessageToAllPlayers() {
        when(world.getPlayers()).thenReturn(List.of(player1, player2));

        manager.broadcastToWorld(world, "<green>Test broadcast");

        verify(player1).sendMessage(any(Component.class));
        verify(player2).sendMessage(any(Component.class));
    }
```

Run: `./gradlew :haven-core:test --tests "*.SleepManagerTest" -i`
Expected: FAIL — `sendActionBarToWorld` and `broadcastToWorld` are stubs.

- [ ] **Step 2: Implement `sendActionBarToWorld` and `broadcastToWorld` in SleepManager.java**

Replace the stub methods:

```java
    void sendActionBarToWorld(World world) {
        State state = worldStates.getOrDefault(world.getName(), State.IDLE);
        String template = switch (state) {
            case WAITING, SKIPPING -> settings.messages().sleeping();
            case PAUSED -> settings.messages().skipPaused();
            default -> null;
        };
        if (template == null) return;

        int sleeping = getSleepingCount(world);
        int active   = countActive(world);
        String formatted = template
            .replace("<sleeping>", String.valueOf(sleeping))
            .replace("<active>",   String.valueOf(active));
        Component bar = MM.deserialize(formatted);
        for (Player p : world.getPlayers()) {
            p.sendActionBar(bar);
        }
    }

    void broadcastToWorld(World world, String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) return;
        Component msg = MM.deserialize(miniMessage);
        for (Player p : world.getPlayers()) {
            p.sendMessage(msg);
        }
    }
```

- [ ] **Step 3: Run all sleep tests**

Run: `./gradlew :haven-core:test --tests "*.SleepManagerTest" -i`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepManager.java \
        haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepManagerTest.java
git commit -m "feat(sleep): implement action bar feedback and chat broadcast"
```

---

## Task 8: SleepAdminCommand

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepAdminCommand.java`
- Test: `haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepAdminCommandTest.java`

- [ ] **Step 1: Write failing test**

```java
package dev.invisiblespiders.haven.core.sleep;

import dev.invisiblespiders.haven.api.service.HavenEventBus;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SleepAdminCommandTest {

    @Mock Plugin plugin;
    @Mock Server server;
    @Mock HavenEventBus eventBus;
    @Mock Logger logger;
    @Mock World world;
    @Mock Player sender;

    SleepManager manager;
    SleepAdminCommand command;

    @BeforeEach
    void setup() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(world.getName()).thenReturn("world");
        when(sender.getWorld()).thenReturn(world);
        SleepSettings settings = SleepSettings.from(new YamlConfiguration());
        manager = new SleepManager(settings, null, eventBus, plugin, logger);
        command = new SleepAdminCommand(manager, plugin);
    }

    @Test
    void statusSendsMessageToSender() {
        when(world.getPlayers()).thenReturn(List.of());
        when(server.getWorld("world")).thenReturn(world);

        command.execute(sender, new String[]{"status"});

        verify(sender, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void toggleDisablesWorldAndReturnsNewState() {
        boolean result = manager.toggle(world);
        assertThat(result).isFalse();
        assertThat(manager.isEligible(world)).isFalse();
    }

    @Test
    void toggleReEnablesWorldAfterDisable() {
        manager.toggle(world); // disable
        boolean result = manager.toggle(world); // re-enable
        assertThat(result).isTrue();
        assertThat(manager.isEligible(world)).isTrue();
    }

    @Test
    void tabCompleteLevel1ReturnsSubcommands() {
        List<String> completions = command.tabComplete(sender, new String[]{""});
        assertThat(completions).containsExactlyInAnyOrder("skip", "toggle", "status");
    }

    @Test
    void tabCompleteFiltersByPrefix() {
        List<String> completions = command.tabComplete(sender, new String[]{"sk"});
        assertThat(completions).containsExactly("skip");
    }
}
```

Run: `./gradlew :haven-core:test --tests "*.SleepAdminCommandTest" -i`
Expected: FAIL — `SleepAdminCommand` does not exist.

- [ ] **Step 2: Create SleepAdminCommand.java**

```java
package dev.invisiblespiders.haven.core.sleep;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SleepAdminCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SleepManager sleepManager;
    private final Plugin plugin;

    public SleepAdminCommand(SleepManager sleepManager, Plugin plugin) {
        this.sleepManager = sleepManager;
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) { sendUsage(sender); return; }
        switch (args[0].toLowerCase()) {
            case "skip"   -> handleSkip(sender, args);
            case "toggle" -> handleToggle(sender, args);
            case "status" -> handleStatus(sender);
            default       -> sendUsage(sender);
        }
    }

    private void handleSkip(CommandSender sender, String[] args) {
        World world = resolveWorld(sender, args, 1);
        if (world == null) return;
        if (!sleepManager.isEligible(world)) {
            sender.sendMessage(MM.deserialize(
                "<red>'" + world.getName() + "' is not in the sleep module's world list."));
            return;
        }
        sleepManager.forceSkip(world);
        sender.sendMessage(MM.deserialize("<green>Force-triggered night skip in " + world.getName() + "."));
    }

    private void handleToggle(CommandSender sender, String[] args) {
        World world = resolveWorld(sender, args, 1);
        if (world == null) return;
        boolean newState = sleepManager.toggle(world);
        sender.sendMessage(MM.deserialize(
            "<green>Sleep module in " + world.getName()
            + " is now " + (newState ? "<green>enabled" : "<red>disabled") + "<green>."));
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold><bold>Sleep Module Status</bold>"));
        for (String worldName : sleepManager.getSettings().worlds()) {
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                sender.sendMessage(MM.deserialize("  <red>" + worldName + " <dark_gray>[WORLD NOT LOADED]"));
                continue;
            }
            String state    = sleepManager.getState(world).name();
            int sleeping    = sleepManager.getSleepingCount(world);
            int active      = sleepManager.getActiveCount(world);
            boolean enabled = sleepManager.isEligible(world);
            sender.sendMessage(MM.deserialize(
                "  <gray>" + worldName
                + " <dark_gray>[" + state + "]"
                + (enabled ? "" : " <red>[DISABLED]")
                + " <gray>sleeping=<white>" + sleeping
                + " <gray>active=<white>" + active));
        }
    }

    private @Nullable World resolveWorld(CommandSender sender, String[] args, int argIndex) {
        String name;
        if (args.length > argIndex) {
            name = args[argIndex];
        } else if (sender instanceof Player player) {
            name = player.getWorld().getName();
        } else {
            sender.sendMessage(MM.deserialize(
                "<red>Specify a world: /haven sleep <command> <world>"));
            return null;
        }
        World world = plugin.getServer().getWorld(name);
        if (world == null) {
            sender.sendMessage(MM.deserialize("<red>World not found: " + name));
        }
        return world;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MM.deserialize(
            "<gray>Usage: /haven sleep <skip|toggle|status> [world]"));
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return List.of("skip", "toggle", "status").stream()
                .filter(s -> s.startsWith(prefix))
                .toList();
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("skip") || args[0].equalsIgnoreCase("toggle"))) {
            String prefix = args[1].toLowerCase();
            return plugin.getServer().getWorlds().stream()
                .map(World::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .toList();
        }
        return List.of();
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :haven-core:test --tests "*.SleepAdminCommandTest" -i`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/sleep/SleepAdminCommand.java \
        haven-core/src/test/java/dev/invisiblespiders/haven/core/sleep/SleepAdminCommandTest.java
git commit -m "feat(sleep): add SleepAdminCommand with skip, toggle, and status subcommands"
```

---

## Task 9: Wiring — HavenCore + HavenCommand + CoreDiagnostics

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java`
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/command/HavenCommand.java`
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/diagnostic/CoreDiagnostics.java`

- [ ] **Step 1: Add sleep wiring to HavenCore.java**

Add the following imports at the top of `HavenCore.java`:

```java
import dev.invisiblespiders.haven.api.service.HavenSleepService;
import dev.invisiblespiders.haven.core.sleep.SleepAdminCommand;
import dev.invisiblespiders.haven.core.sleep.SleepManager;
import dev.invisiblespiders.haven.core.sleep.SleepSettings;
```

In `onEnable()`, add **after the Late-inject AFK broadcast delegate block** and **before the Commands block**:

```java
        // ── Sleep ─────────────────────────────────────────────────────────────
        SleepAdminCommand sleepAdmin = null;
        if (configManager.getMain().getBoolean("features.sleep", true)) {
            SleepSettings sleepSettings = SleepSettings.from(configManager.getSleep());
            SleepManager sleepManager = new SleepManager(
                sleepSettings,
                afkManager,          // null-safe: SleepManager accepts null HavenAfkService
                eventBus,
                this,
                getLogger()
            );
            sleepManager.onEnable();
            getServer().getPluginManager().registerEvents(sleepManager, this);
            sm.register(HavenSleepService.class, sleepManager, this, ServicePriority.Normal);
            sleepAdmin = new SleepAdminCommand(sleepManager, this);
            getLogger().info("Sleep module enabled.");
        }
```

In the Commands block, pass `sleepAdmin` to `HavenCommand` via a setter call immediately after `cmd` is constructed (see Step 2 for the setter):

```java
        HavenCommand cmd = new HavenCommand(
            this, configManager, hookRegistry, asyncExecutor, opToggleService, suiteRegistry,
            upgradeAdmin, rewardAdmin
        );
        if (sleepAdmin != null) cmd.setSleepAdmin(sleepAdmin);   // ← add this line
        var havenCmd = getCommand("haven");
```

In `onDisable()`, the sleep module's `onDisable()` is handled automatically since `SleepManager` is registered as a Bukkit `Listener` and services are unregistered. No additional teardown needed.

- [ ] **Step 2: Add sleep subcommand to HavenCommand.java**

Add field:
```java
    private @Nullable SleepAdminCommand sleepAdminCommand;
```

Add setter (after the constructor chain):
```java
    public void setSleepAdmin(SleepAdminCommand sleepAdminCommand) {
        this.sleepAdminCommand = sleepAdminCommand;
    }
```

Add `sleep` case to `onCommand` switch (after the `rewards` case):
```java
            case "sleep" -> {
                if (!requirePermission(sender, "haven.admin.sleep")) return true;
                if (sleepAdminCommand == null) {
                    sender.sendMessage(MM.deserialize("<red>Sleep module is disabled."));
                } else {
                    sleepAdminCommand.execute(sender, tail(args));
                }
            }
```

Add `sleep` to `onTabComplete` subcommands list:
```java
            if (sleepAdminCommand != null && sender.hasPermission("haven.admin.sleep")) {
                subcommands.add("sleep");
            }
```

Add sleep tab-complete delegation (after the `rewards` tab-complete block):
```java
        if (args.length >= 2 && args[0].equalsIgnoreCase("sleep") && sleepAdminCommand != null) {
            return sleepAdminCommand.tabComplete(sender, tail(args));
        }
```

Update the usage string in `sendHelp`:
```java
        sender.sendMessage(MM.deserialize(
            "<gray>/haven sleep <dark_gray>- <white>Manage sleep module <gray>(haven.admin.sleep)"));
```

- [ ] **Step 3: Add sleep service check to CoreDiagnostics.java**

Add import:
```java
import dev.invisiblespiders.haven.api.service.HavenSleepService;
```

In `run()`, add after `checkRegisteredService(services, HavenCodexService.class, "codex")`:
```java
        if (config.getMain() != null && config.getMain().getBoolean("features.sleep", true)) {
            results.add(checkRegisteredService(services, HavenSleepService.class, "sleep"));
        }
```

- [ ] **Step 4: Full build + test suite**

Run: `./gradlew build test`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java \
        haven-core/src/main/java/dev/invisiblespiders/haven/core/command/HavenCommand.java \
        haven-core/src/main/java/dev/invisiblespiders/haven/core/diagnostic/CoreDiagnostics.java
git commit -m "feat(sleep): wire SleepManager, SleepAdminCommand, and CoreDiagnostics"
```
