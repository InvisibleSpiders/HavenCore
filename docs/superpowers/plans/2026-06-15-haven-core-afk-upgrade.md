# HavenCore AFK Timer Upgrade — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tiered `afk-timer` upgrade to HavenCore's Personal upgrade category so players can extend their AFK timeout through `/upgrades`.

**Architecture:** A new `CoreUpgradeProvider` registers the `personal` category and the `afk-timer` upgrade definition (levels I–V loaded from `upgrades.yml`). `AfkManager` receives a late-injected `HavenUpgradeService` reference and uses `currentLevel(uuid, "afk-timer")` at tick time to compute per-player effective timeout from `AfkSettings`. No new persistence, no new service — rides existing infrastructure.

**Tech Stack:** Java 21, Paper 26.1 API, HavenCore upgrade system (`HavenUpgradeService`, `UpgradeProvider`, `UpgradeLevel`), Bukkit `FileConfiguration`, Gradle (`gradlew.bat`), JUnit 5, Mockito, AssertJ.

**Repo:** `C:\Users\ncobu\claude plugin\HavenAPI`

---

## File Map

| Action | Path |
|--------|------|
| Modify | `haven-core/src/main/java/dev/invisiblespiders/haven/core/afk/AfkSettings.java` |
| Modify | `haven-core/src/main/java/dev/invisiblespiders/haven/core/afk/AfkManager.java` |
| Create | `haven-core/src/main/java/dev/invisiblespiders/haven/core/upgrade/CoreUpgradeProvider.java` |
| Modify | `haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java` |
| Modify | `haven-core/src/main/resources/afk.yml` |
| Modify | `haven-core/src/main/resources/upgrades.yml` |
| Modify | `haven-core/src/test/java/dev/invisiblespiders/haven/core/afk/AfkSettingsTest.java` |
| Create | `haven-core/src/test/java/dev/invisiblespiders/haven/core/upgrade/CoreUpgradeProviderTest.java` |

---

## Task 1: AfkSettings — upgrade bonus config

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/afk/AfkSettings.java`
- Modify: `haven-core/src/main/resources/afk.yml`
- Modify: `haven-core/src/test/java/dev/invisiblespiders/haven/core/afk/AfkSettingsTest.java`

- [ ] **Step 1: Write failing tests first**

Add these two test methods to the existing `AfkSettingsTest` class (after `messageDefaultsPresent`):

```java
@Test
void upgradeTimeoutBonusDefaultsToEmptyList() {
    AfkSettings settings = AfkSettings.from(load(""));
    assertThat(settings.upgradeBonusSeconds(0)).isEqualTo(0);
    assertThat(settings.upgradeBonusSeconds(1)).isEqualTo(0);
    assertThat(settings.upgradeBonusSeconds(5)).isEqualTo(0);
}

@Test
void upgradeTimeoutBonusParsedFromConfig() {
    AfkSettings settings = AfkSettings.from(load("""
        upgrade:
          bonus-seconds:
            - 900
            - 1800
            - 3600
            - 7200
            - 14400
        """));
    assertThat(settings.upgradeBonusSeconds(0)).isEqualTo(0);   // level 0 = no upgrade
    assertThat(settings.upgradeBonusSeconds(1)).isEqualTo(900);
    assertThat(settings.upgradeBonusSeconds(3)).isEqualTo(3600);
    assertThat(settings.upgradeBonusSeconds(5)).isEqualTo(14400);
    assertThat(settings.upgradeBonusSeconds(6)).isEqualTo(0);   // out of range
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
gradlew.bat :haven-core:test --tests "dev.invisiblespiders.haven.core.afk.AfkSettingsTest"
```

Expected: FAIL — `upgradeBonusSeconds` method does not exist.

- [ ] **Step 3: Modify AfkSettings.java**

Replace the entire file with this (all nested records unchanged, only the outer record gains one field and one method):

```java
package dev.invisiblespiders.haven.core.afk;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record AfkSettings(
        int timeout,
        int kickTimeout,
        boolean strictMovement,
        ActivityEvents activityEvents,
        DetectionSettings detection,
        AfkMessages messages,
        List<Integer> upgradeTimeoutBonusSeconds
) {
    public AfkSettings {
        upgradeTimeoutBonusSeconds = List.copyOf(upgradeTimeoutBonusSeconds);
    }

    public static AfkSettings from(FileConfiguration config) {
        return new AfkSettings(
                config.getInt("timeout", 300),
                config.getInt("kick-timeout", 1800),
                config.getBoolean("strict-movement", true),
                ActivityEvents.from(config),
                DetectionSettings.from(config),
                AfkMessages.from(config),
                config.getIntegerList("upgrade.bonus-seconds")
        );
    }

    /** Returns the bonus AFK timeout (seconds) for a given upgrade level. Level 0 or out-of-range returns 0. */
    public int upgradeBonusSeconds(int level) {
        if (level <= 0 || level > upgradeTimeoutBonusSeconds.size()) return 0;
        return upgradeTimeoutBonusSeconds.get(level - 1);
    }

    public record ActivityEvents(boolean movement, boolean keyboardInput, boolean chat,
                                  boolean commands, boolean interact) {
        public static ActivityEvents from(FileConfiguration config) {
            return new ActivityEvents(
                    config.getBoolean("activity-events.movement", true),
                    config.getBoolean("activity-events.keyboard-input", true),
                    config.getBoolean("activity-events.chat", true),
                    config.getBoolean("activity-events.commands", true),
                    config.getBoolean("activity-events.interact", true)
            );
        }
    }

    public record DetectionSettings(float minRotationDelta, int patternMinIdleSeconds, boolean patternAlert,
                                     String patternAlertPermission) {
        public static DetectionSettings from(FileConfiguration config) {
            return new DetectionSettings(
                    (float) config.getDouble("detection.min-rotation-delta", 1.5),
                    config.getInt("detection.pattern-min-idle-seconds", 30),
                    config.getBoolean("detection.pattern-alert", true),
                    config.getString("detection.pattern-alert-permission", "haven.afk.alerts")
            );
        }
    }

    public record AfkMessages(String afkBroadcast, String returnBroadcast,
                               String actionBar, String kickReason) {
        public static AfkMessages from(FileConfiguration config) {
            return new AfkMessages(
                    config.getString("messages.afk-broadcast", "<gray><player> is now AFK."),
                    config.getString("messages.return-broadcast", "<gray><player> is no longer AFK."),
                    config.getString("messages.action-bar", "<yellow>You are AFK. Move to return."),
                    config.getString("messages.kick-reason", "You were kicked for being AFK.")
            );
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
gradlew.bat :haven-core:test --tests "dev.invisiblespiders.haven.core.afk.AfkSettingsTest"
```

Expected: all 5 tests PASS.

- [ ] **Step 5: Update afk.yml default config**

Add this block to the end of `haven-core/src/main/resources/afk.yml`:

```yaml

# AFK Timer upgrade bonus seconds per level (indexed 1–5).
# Level 1 = first tier purchased, Level 5 = max tier.
# Set to 0 to disable upgrade integration at a specific level.
upgrade:
  bonus-seconds:
    - 900    # Level I: +15 min
    - 1800   # Level II: +30 min
    - 3600   # Level III: +60 min
    - 7200   # Level IV: +120 min
    - 14400  # Level V: +240 min
```

- [ ] **Step 6: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/afk/AfkSettings.java
git add haven-core/src/main/resources/afk.yml
git add haven-core/src/test/java/dev/invisiblespiders/haven/core/afk/AfkSettingsTest.java
git commit -m "feat: add upgrade bonus seconds to AfkSettings"
```

---

## Task 2: AfkManager — per-player effective timeout

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/afk/AfkManager.java`

- [ ] **Step 1: Write failing test**

Create `haven-core/src/test/java/dev/invisiblespiders/haven/core/afk/AfkManagerUpgradeTest.java`:

```java
package dev.invisiblespiders.haven.core.afk;

import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AfkManagerUpgradeTest {

    @Mock Plugin plugin;
    @Mock Server server;
    @Mock HavenPlayerService playerService;
    @Mock HavenUpgradeService upgradeService;

    AfkSettings settings;
    AfkManager manager;
    UUID uuid;

    @BeforeEach
    void setup() throws Exception {
        uuid = UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
            timeout: 300
            upgrade:
              bonus-seconds:
                - 900
                - 1800
                - 3600
                - 7200
                - 14400
            """);
        settings = AfkSettings.from(config);
        manager = new AfkManager(settings, plugin, playerService);
    }

    @Test
    void effectiveTimeoutIsBaseTimeoutWhenNoUpgradeService() throws Exception {
        long result = invokeEffectiveTimeout(manager, uuid);
        assertThat(result).isEqualTo(300L);
    }

    @Test
    void effectiveTimeoutIsBaseWhenUpgradeServiceReturnsLevel0() throws Exception {
        when(upgradeService.currentLevel(uuid, "afk-timer")).thenReturn(0);
        manager.setUpgradeService(upgradeService);

        long result = invokeEffectiveTimeout(manager, uuid);
        assertThat(result).isEqualTo(300L);
    }

    @Test
    void effectiveTimeoutAddsLevelBonusSeconds() throws Exception {
        when(upgradeService.currentLevel(uuid, "afk-timer")).thenReturn(2);
        manager.setUpgradeService(upgradeService);

        long result = invokeEffectiveTimeout(manager, uuid);
        assertThat(result).isEqualTo(300L + 1800L); // base + level-2 bonus
    }

    @Test
    void effectiveTimeoutClipsOutOfRangeLevelToZeroBonus() throws Exception {
        when(upgradeService.currentLevel(uuid, "afk-timer")).thenReturn(99);
        manager.setUpgradeService(upgradeService);

        long result = invokeEffectiveTimeout(manager, uuid);
        assertThat(result).isEqualTo(300L); // no bonus for invalid level
    }

    private static long invokeEffectiveTimeout(AfkManager manager, UUID uuid) throws Exception {
        Method m = AfkManager.class.getDeclaredMethod("effectiveTimeout", UUID.class);
        m.setAccessible(true);
        return (long) m.invoke(manager, uuid);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
gradlew.bat :haven-core:test --tests "dev.invisiblespiders.haven.core.afk.AfkManagerUpgradeTest"
```

Expected: FAIL — `setUpgradeService` and `effectiveTimeout` do not exist.

- [ ] **Step 3: Add field, setter, and helper to AfkManager.java**

Add the import at the top of the imports block:
```java
import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
```

Add these fields after the existing `private @Nullable BukkitTask schedulerTask;` line:
```java
private @Nullable HavenUpgradeService upgradeService;
```

Add this method after `setTabManager()`:
```java
public void setUpgradeService(@Nullable HavenUpgradeService upgradeService) {
    this.upgradeService = upgradeService;
}
```

Add this private method after `setUpgradeService()`:
```java
private long effectiveTimeout(UUID uuid) {
    if (upgradeService == null) return settings.timeout();
    int level = upgradeService.currentLevel(uuid, "afk-timer");
    return settings.timeout() + settings.upgradeBonusSeconds(level);
}
```

- [ ] **Step 4: Replace timeout check in tick()**

In the `tick()` method, replace:
```java
if (!isAfk(uuid) && idleSeconds >= settings.timeout()) {
```
with:
```java
if (!isAfk(uuid) && idleSeconds >= effectiveTimeout(uuid)) {
```

- [ ] **Step 5: Run tests to verify they pass**

```
gradlew.bat :haven-core:test --tests "dev.invisiblespiders.haven.core.afk.AfkManagerUpgradeTest"
gradlew.bat :haven-core:test --tests "dev.invisiblespiders.haven.core.afk.AfkManagerTest"
```

Expected: all tests PASS (AfkManagerTest must still pass — the change is backward-compatible when upgradeService is null).

- [ ] **Step 6: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/afk/AfkManager.java
git add haven-core/src/test/java/dev/invisiblespiders/haven/core/afk/AfkManagerUpgradeTest.java
git commit -m "feat: per-player effective AFK timeout via upgrade level"
```

---

## Task 3: CoreUpgradeProvider + upgrades.yml

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/upgrade/CoreUpgradeProvider.java`
- Modify: `haven-core/src/main/resources/upgrades.yml`
- Create: `haven-core/src/test/java/dev/invisiblespiders/haven/core/upgrade/CoreUpgradeProviderTest.java`

- [ ] **Step 1: Write failing tests**

Create `CoreUpgradeProviderTest.java`:

```java
package dev.invisiblespiders.haven.core.upgrade;

import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreUpgradeProviderTest {

    private static final String FIVE_LEVEL_CONFIG = """
        personal:
          afk-timer:
            names:
              - "AFK Timer I"
              - "AFK Timer II"
              - "AFK Timer III"
              - "AFK Timer IV"
              - "AFK Timer V"
            costs:
              - 5000.0
              - 15000.0
              - 35000.0
              - 75000.0
              - 150000.0
        """;

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @Test
    void providerIdAndCategoryAreCorrect() {
        CoreUpgradeProvider provider = new CoreUpgradeProvider(load(FIVE_LEVEL_CONFIG), mock(HavenEconomyService.class));
        assertThat(provider.id()).isEqualTo("haven-core");
        assertThat(provider.categories()).hasSize(1);
        assertThat(provider.categories().get(0).id()).isEqualTo("personal");
    }

    @Test
    void loadsAfkTimerDefinitionWithFiveLevels() {
        CoreUpgradeProvider provider = new CoreUpgradeProvider(load(FIVE_LEVEL_CONFIG), mock(HavenEconomyService.class));
        List<UpgradeDefinition> defs = provider.definitions();
        assertThat(defs).hasSize(1);
        UpgradeDefinition afk = defs.get(0);
        assertThat(afk.id()).isEqualTo("afk-timer");
        assertThat(afk.providerId()).isEqualTo("haven-core");
        assertThat(afk.category().id()).isEqualTo("personal");
        assertThat(afk.levels()).hasSize(5);
    }

    @Test
    void levelNumbersAreSequential() {
        CoreUpgradeProvider provider = new CoreUpgradeProvider(load(FIVE_LEVEL_CONFIG), mock(HavenEconomyService.class));
        List<UpgradeLevel> levels = provider.definitions().get(0).levels();
        for (int i = 0; i < levels.size(); i++) {
            assertThat(levels.get(i).level()).isEqualTo(i + 1);
        }
    }

    @Test
    void eachLevelHasOneMoneyRequirement() {
        CoreUpgradeProvider provider = new CoreUpgradeProvider(load(FIVE_LEVEL_CONFIG), mock(HavenEconomyService.class));
        List<UpgradeLevel> levels = provider.definitions().get(0).levels();
        for (UpgradeLevel level : levels) {
            assertThat(level.requirements()).hasSize(1);
            assertThat(level.requirements().get(0).type()).isEqualTo("money");
        }
    }

    @Test
    void eachLevelHasNoEffects() {
        CoreUpgradeProvider provider = new CoreUpgradeProvider(load(FIVE_LEVEL_CONFIG), mock(HavenEconomyService.class));
        for (UpgradeLevel level : provider.definitions().get(0).levels()) {
            assertThat(level.effects()).isEmpty();
        }
    }

    @Test
    void emptyConfigProducesNoDefinitions() {
        CoreUpgradeProvider provider = new CoreUpgradeProvider(load(""), mock(HavenEconomyService.class));
        assertThat(provider.definitions()).isEmpty();
    }

    @Test
    void requirementFactoryReturnsMoney() {
        CoreUpgradeProvider provider = new CoreUpgradeProvider(load(""), mock(HavenEconomyService.class));
        assertThat(provider.requirement("money", java.util.Map.of("amount", "100"))).isPresent();
        assertThat(provider.requirement("money", java.util.Map.of("amount", "100")).get().type()).isEqualTo("money");
    }

    @Test
    void requirementFactoryReturnsEmptyForUnknownType() {
        CoreUpgradeProvider provider = new CoreUpgradeProvider(load(""), mock(HavenEconomyService.class));
        assertThat(provider.requirement("unknown", java.util.Map.of())).isEmpty();
    }

    @Test
    void effectFactoryAlwaysReturnsEmpty() {
        CoreUpgradeProvider provider = new CoreUpgradeProvider(load(""), mock(HavenEconomyService.class));
        assertThat(provider.effect("afk-duration", java.util.Map.of())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
gradlew.bat :haven-core:test --tests "dev.invisiblespiders.haven.core.upgrade.CoreUpgradeProviderTest"
```

Expected: FAIL — class does not exist.

- [ ] **Step 3: Create CoreUpgradeProvider.java**

Create `haven-core/src/main/java/dev/invisiblespiders/haven/core/upgrade/CoreUpgradeProvider.java`:

```java
package dev.invisiblespiders.haven.core.upgrade;

import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeCategory;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeEffect;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradeProvider;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeScope;
import dev.invisiblespiders.haven.api.upgrade.UpgradeVisibility;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CoreUpgradeProvider implements UpgradeProvider {

    static final String PROVIDER_ID = "haven-core";
    private static final UpgradeCategory PERSONAL = new UpgradeCategory("personal", "Personal", "⭐", 1);

    private final List<UpgradeDefinition> definitions;
    private final HavenEconomyService economy;

    public CoreUpgradeProvider(FileConfiguration upgradesConfig, HavenEconomyService economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.definitions = loadDefinitions(upgradesConfig);
    }

    @Override
    public String id() { return PROVIDER_ID; }

    @Override
    public String displayName() { return "Core"; }

    @Override
    public List<UpgradeCategory> categories() { return List.of(PERSONAL); }

    @Override
    public List<UpgradeDefinition> definitions() { return definitions; }

    @Override
    public Optional<UpgradeEffect> effect(String type, Map<String, String> values) {
        return Optional.empty();
    }

    @Override
    public Optional<UpgradeRequirement> requirement(String type, Map<String, String> values) {
        return switch (type) {
            case "money" -> Optional.of(new MoneyRequirement(economy,
                    Double.parseDouble(values.getOrDefault("amount", "0"))));
            case "permission" -> Optional.of(new PermissionRequirement(
                    values.getOrDefault("node", "")));
            default -> Optional.empty();
        };
    }

    private List<UpgradeDefinition> loadDefinitions(FileConfiguration config) {
        ConfigurationSection afkSection = config.getConfigurationSection("personal.afk-timer");
        if (afkSection == null) return List.of();

        List<Double> costs = afkSection.getDoubleList("costs");
        List<String> names = afkSection.getStringList("names");
        if (costs.isEmpty()) return List.of();

        List<UpgradeLevel> levels = new ArrayList<>();
        for (int i = 0; i < costs.size(); i++) {
            int levelNum = i + 1;
            String displayName = i < names.size() ? names.get(i) : "AFK Timer " + toRoman(levelNum);
            List<UpgradeRequirement> reqs = costs.get(i) > 0
                    ? List.of(new MoneyRequirement(economy, costs.get(i)))
                    : List.of();
            levels.add(new UpgradeLevel(levelNum, displayName, reqs, List.of(), Map.of()));
        }

        return List.of(new UpgradeDefinition(
                "afk-timer", PROVIDER_ID, PERSONAL,
                UpgradeScope.PLAYER, UpgradeVisibility.VISIBLE, null, levels));
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI";
            case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
gradlew.bat :haven-core:test --tests "dev.invisiblespiders.haven.core.upgrade.CoreUpgradeProviderTest"
```

Expected: all 9 tests PASS.

- [ ] **Step 5: Update upgrades.yml**

Add this block to `haven-core/src/main/resources/upgrades.yml` (after the existing `ui:` block):

```yaml

# Personal upgrades — registered by CoreUpgradeProvider.
personal:
  afk-timer:
    names:
      - "AFK Timer I"
      - "AFK Timer II"
      - "AFK Timer III"
      - "AFK Timer IV"
      - "AFK Timer V"
    costs:
      - 5000.0
      - 15000.0
      - 35000.0
      - 75000.0
      - 150000.0
```

- [ ] **Step 6: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/upgrade/CoreUpgradeProvider.java
git add haven-core/src/main/resources/upgrades.yml
git add haven-core/src/test/java/dev/invisiblespiders/haven/core/upgrade/CoreUpgradeProviderTest.java
git commit -m "feat: CoreUpgradeProvider with personal/afk-timer upgrade"
```

---

## Task 4: Wire CoreUpgradeProvider into HavenCore

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java`

- [ ] **Step 1: Add import**

Add this import to `HavenCore.java` (with the other upgrade imports):
```java
import dev.invisiblespiders.haven.core.upgrade.CoreUpgradeProvider;
```

- [ ] **Step 2: Register CoreUpgradeProvider after upgradeService.setDialog()**

Find this block (around line 165–166):
```java
UpgradeDialog upgradeDialog = new UpgradeDialog(configManager, upgradeService);
upgradeService.setDialog(upgradeDialog);
```

Add immediately after:
```java
CoreUpgradeProvider coreProvider = new CoreUpgradeProvider(configManager.getUpgrades(), economyService);
upgradeService.registerProvider(coreProvider);
```

- [ ] **Step 3: Inject upgradeService into AfkManager**

Find this block in the AFK section (around line 211–218):
```java
if (configManager.getMain().getBoolean("features.afk", true)) {
    AfkSettings afkSettings = AfkSettings.from(configManager.getAfk());
    afkManager = new AfkManager(afkSettings, this, playerService);
    getServer().getPluginManager().registerEvents(afkManager, this);
    afkManager.start();
    sm.register(HavenAfkService.class, afkManager, this, ServicePriority.Normal);
    getLogger().info("AFK detection enabled.");
}
```

Add `afkManager.setUpgradeService(upgradeService);` after `afkManager.start();`:
```java
if (configManager.getMain().getBoolean("features.afk", true)) {
    AfkSettings afkSettings = AfkSettings.from(configManager.getAfk());
    afkManager = new AfkManager(afkSettings, this, playerService);
    getServer().getPluginManager().registerEvents(afkManager, this);
    afkManager.start();
    afkManager.setUpgradeService(upgradeService);
    sm.register(HavenAfkService.class, afkManager, this, ServicePriority.Normal);
    getLogger().info("AFK detection enabled.");
}
```

- [ ] **Step 4: Run full test suite**

```
gradlew.bat :haven-core:test
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java
git commit -m "feat: register CoreUpgradeProvider and wire AFK upgrade service"
```

- [ ] **Step 6: Push**

```
git push
```

---

## Verification checklist

After all tasks complete:
- [ ] `/upgrades` in-game shows a "Personal" category with AFK Timer I–V
- [ ] Purchasing AFK Timer II sets player to level 2 in the database (check via `/haven admin upgrade` or DB query)
- [ ] A player at level 2 goes AFK after `300 + 1800 = 2100` seconds of inactivity (not 300)
- [ ] A player at level 0 (no upgrade) still goes AFK at 300 seconds
- [ ] Reload (`/haven reload`) does not lose the CoreUpgradeProvider registration (provider is re-created in `onEnable`, not stored across reloads)
