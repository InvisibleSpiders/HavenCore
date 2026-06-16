# HavenTeleport — Personal Upgrades, Warmup Action Bar & Homes Dialog

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add home/warp/shop slot tier upgrades hooked into HavenCore's `/upgrades` UI, add an action bar progress animation to the existing teleport warmup, and redesign the `/homes` dialog for clarity.

**Architecture:** `TeleportUpgradeProvider` implements `UpgradeProvider` and registers three upgrades (`home-slots`, `warp-slots`, `shop-slots`) with HavenCore at plugin enable. The existing slot-limit system reads `upgradeService.currentLevel(uuid, upgradeId)` to determine max slots. `TeleportWarmupService` gains an action bar countdown animation. `DialogMenuService.homesMenu()` is rewritten to show slot count, per-home sub-dialogs, and a clear "Add Home" button.

**Tech Stack:** Java 21, Paper 26.1 Dialog API, HavenCore `HavenUpgradeService` / `UpgradeProvider` API, `com.nick.teleportlocations` base package, Gradle.

**Repo:** https://github.com/InvisibleSpiders/HavenTeleport  
Clone and work from the repo root. Ensure HavenAPI (HavenCore) jars are available as local dependencies.

---

## Prerequisites — read these files before implementing

Before starting any task, read the following files from the repo:

1. `src/main/java/com/nick/teleportlocations/TeleportLocationsPlugin.java` — to understand onEnable wiring
2. `src/main/java/com/nick/teleportlocations/RuntimeServices.java` — DI record that holds all services
3. `src/main/java/com/nick/teleportlocations/limit/` — all files — this is the system that enforces home/warp/shop slot limits; you will integrate upgrade levels here
4. `src/main/java/com/nick/teleportlocations/tpa/TeleportWarmupService.java` — existing warmup implementation
5. `src/main/java/com/nick/teleportlocations/dialog/DialogMenuService.java` — current homes/warps/shops menu generation
6. `src/main/java/com/nick/teleportlocations/dialog/PaperDialogPresenter.java` — converts `DialogMenuModel` to Paper Dialog API
7. `src/main/java/com/nick/teleportlocations/dialog/DialogActionRouter.java` — parses action key strings
8. `src/main/java/com/nick/teleportlocations/dialog/DialogActionExecutor.java` — executes routed actions
9. `src/main/resources/config.yml` — to understand configurable values

---

## File Map

| Action | Path |
|--------|------|
| Create | `src/main/java/com/nick/teleportlocations/upgrade/TeleportUpgradeProvider.java` |
| Modify | `src/main/java/com/nick/teleportlocations/limit/` — (read first to identify exact class) |
| Modify | `src/main/java/com/nick/teleportlocations/tpa/TeleportWarmupService.java` |
| Modify | `src/main/java/com/nick/teleportlocations/dialog/DialogMenuService.java` |
| Modify | `src/main/java/com/nick/teleportlocations/dialog/DialogActionRouter.java` |
| Modify | `src/main/java/com/nick/teleportlocations/dialog/DialogActionExecutor.java` |
| Modify | `src/main/java/com/nick/teleportlocations/RuntimeServices.java` |
| Modify | `src/main/java/com/nick/teleportlocations/TeleportLocationsPlugin.java` |
| Modify | `src/main/resources/config.yml` |

---

## Task 1: TeleportUpgradeProvider

**Files:**
- Create: `src/main/java/com/nick/teleportlocations/upgrade/TeleportUpgradeProvider.java`

This registers three upgrades with HavenCore's upgrade system. The actual slot limit enforcement is done in Task 2.

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/nick/teleportlocations/upgrade/TeleportUpgradeProviderTest.java`:

```java
package com.nick.teleportlocations.upgrade;

import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeleportUpgradeProviderTest {

    private static final String CONFIG = """
        upgrades:
          home-slots:
            names: ["Home Slots I", "Home Slots II", "Home Slots III", "Home Slots IV", "Home Slots V"]
            costs: [10000.0, 25000.0, 50000.0, 100000.0, 200000.0]
          warp-slots:
            names: ["Warp Slots I", "Warp Slots II", "Warp Slots III", "Warp Slots IV", "Warp Slots V"]
            costs: [10000.0, 25000.0, 50000.0, 100000.0, 200000.0]
          shop-slots:
            names: ["Shop Slots I", "Shop Slots II", "Shop Slots III", "Shop Slots IV", "Shop Slots V"]
            costs: [10000.0, 25000.0, 50000.0, 100000.0, 200000.0]
        """;

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration cfg = new YamlConfiguration();
        try { cfg.loadFromString(yaml); } catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return cfg;
    }

    @Test
    void providerIdAndDisplayName() {
        TeleportUpgradeProvider p = new TeleportUpgradeProvider(load(CONFIG), null);
        assertThat(p.id()).isEqualTo("haven-teleport");
        assertThat(p.displayName()).isEqualTo("Teleport");
    }

    @Test
    void registersPersonalCategory() {
        TeleportUpgradeProvider p = new TeleportUpgradeProvider(load(CONFIG), null);
        assertThat(p.categories()).hasSize(1);
        assertThat(p.categories().get(0).id()).isEqualTo("personal");
    }

    @Test
    void loadsThreeDefinitions() {
        TeleportUpgradeProvider p = new TeleportUpgradeProvider(load(CONFIG), null);
        assertThat(p.definitions()).hasSize(3);
        List<String> ids = p.definitions().stream().map(UpgradeDefinition::id).toList();
        assertThat(ids).containsExactlyInAnyOrder("home-slots", "warp-slots", "shop-slots");
    }

    @Test
    void eachDefinitionHasFiveLevels() {
        TeleportUpgradeProvider p = new TeleportUpgradeProvider(load(CONFIG), null);
        for (UpgradeDefinition def : p.definitions()) {
            assertThat(def.levels()).hasSize(5).as("upgrade %s should have 5 levels", def.id());
        }
    }

    @Test
    void levelNumbersAreSequential() {
        TeleportUpgradeProvider p = new TeleportUpgradeProvider(load(CONFIG), null);
        for (UpgradeDefinition def : p.definitions()) {
            List<UpgradeLevel> levels = def.levels();
            for (int i = 0; i < levels.size(); i++) {
                assertThat(levels.get(i).level()).isEqualTo(i + 1);
            }
        }
    }

    @Test
    void effectFactoryAlwaysEmpty() {
        TeleportUpgradeProvider p = new TeleportUpgradeProvider(load(CONFIG), null);
        assertThat(p.effect("any", java.util.Map.of())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew test --tests "com.nick.teleportlocations.upgrade.TeleportUpgradeProviderTest"
```

Expected: FAIL — class does not exist.

- [ ] **Step 3: Create TeleportUpgradeProvider.java**

```java
package com.nick.teleportlocations.upgrade;

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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TeleportUpgradeProvider implements UpgradeProvider {

    public static final String PROVIDER_ID = "haven-teleport";
    private static final UpgradeCategory PERSONAL = new UpgradeCategory("personal", "Personal", "⭐", 1);

    private static final String[] UPGRADE_IDS = {"home-slots", "warp-slots", "shop-slots"};

    private final List<UpgradeDefinition> definitions;
    private final @Nullable HavenEconomyService economy;

    public TeleportUpgradeProvider(FileConfiguration config, @Nullable HavenEconomyService economy) {
        this.economy = economy;
        this.definitions = loadDefinitions(config);
    }

    @Override
    public String id() { return PROVIDER_ID; }

    @Override
    public String displayName() { return "Teleport"; }

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
        return Optional.empty();
    }

    private List<UpgradeDefinition> loadDefinitions(FileConfiguration config) {
        List<UpgradeDefinition> result = new ArrayList<>();
        for (String upgradeId : UPGRADE_IDS) {
            ConfigurationSection section = config.getConfigurationSection("upgrades." + upgradeId);
            if (section == null) continue;
            List<Double> costs = section.getDoubleList("costs");
            List<String> names = section.getStringList("names");
            if (costs.isEmpty()) continue;

            List<UpgradeLevel> levels = new ArrayList<>();
            for (int i = 0; i < costs.size(); i++) {
                int levelNum = i + 1;
                String displayName = i < names.size() ? names.get(i) : upgradeId + " " + toRoman(levelNum);
                List<UpgradeRequirement> reqs = (economy != null && costs.get(i) > 0)
                        ? List.of(new MoneyRequirement(economy, costs.get(i)))
                        : List.of();
                levels.add(new UpgradeLevel(levelNum, displayName, reqs, List.of(), Map.of()));
            }

            result.add(new UpgradeDefinition(
                    upgradeId, PROVIDER_ID, PERSONAL,
                    UpgradeScope.PLAYER, UpgradeVisibility.VISIBLE, null, levels));
        }
        return List.copyOf(result);
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

**Note:** `MoneyRequirement` here must be a local copy or re-use from HavenCore's API. If HavenCore's `MoneyRequirement` is in `haven-core` (not `haven-api`), create a local `MoneyRequirement` in `com.nick.teleportlocations.upgrade` that implements `UpgradeRequirement` with the same pattern as HavenCore's version:

```java
package com.nick.teleportlocations.upgrade;

import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirementResult;

import java.util.Objects;

final class MoneyRequirement implements UpgradeRequirement {

    private final HavenEconomyService economy;
    private final double amount;

    MoneyRequirement(HavenEconomyService economy, double amount) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.amount = amount;
    }

    @Override public String type() { return "money"; }

    @Override
    public UpgradeRequirementResult validate(UpgradeContext context) {
        return economy.has(context.targetPlayerId(), amount)
                ? UpgradeRequirementResult.success()
                : UpgradeRequirementResult.failure("insufficient-money", "Insufficient money.");
    }

    @Override
    public void consume(UpgradeContext context) {
        if (!economy.withdraw(context.targetPlayerId(), amount))
            throw new IllegalStateException("money withdrawal failed");
    }

    @Override
    public void refund(UpgradeContext context) {
        economy.deposit(context.targetPlayerId(), amount);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew test --tests "com.nick.teleportlocations.upgrade.TeleportUpgradeProviderTest"
```

Expected: all 6 tests PASS.

- [ ] **Step 5: Add upgrade config section to config.yml**

Add to `src/main/resources/config.yml`:

```yaml
upgrades:
  home-slots:
    names: ["Home Slots I", "Home Slots II", "Home Slots III", "Home Slots IV", "Home Slots V"]
    costs: [10000.0, 25000.0, 50000.0, 100000.0, 200000.0]
  warp-slots:
    names: ["Warp Slots I", "Warp Slots II", "Warp Slots III", "Warp Slots IV", "Warp Slots V"]
    costs: [10000.0, 25000.0, 50000.0, 100000.0, 200000.0]
  shop-slots:
    names: ["Shop Slots I", "Shop Slots II", "Shop Slots III", "Shop Slots IV", "Shop Slots V"]
    costs: [10000.0, 25000.0, 50000.0, 100000.0, 200000.0]
```

- [ ] **Step 6: Commit**

```
git add src/main/java/com/nick/teleportlocations/upgrade/
git add src/main/resources/config.yml
git add src/test/java/com/nick/teleportlocations/upgrade/
git commit -m "feat: TeleportUpgradeProvider with home/warp/shop slot upgrades"
```

---

## Task 2: Slot Limit Integration

**Files:**
- Read all files under `src/main/java/com/nick/teleportlocations/limit/` first
- Modify the class that currently returns max home/warp/shop counts per player

The limit system determines how many homes/warps/shops a player can create. It currently reads from config or permissions. We need it to also check the player's upgrade level.

- [ ] **Step 1: Read the limit package**

Read all files in `src/main/java/com/nick/teleportlocations/limit/`. Identify:
- Which class/method returns the max home count for a player?
- Which class/method returns the max warp count?
- Which class/method returns the max shop count?
- How does it currently resolve the limit (config value? permission node? per-tier?)

- [ ] **Step 2: Write failing tests**

Based on what you read, write tests that verify:

```java
// Example structure — adapt to actual class names found in step 1
@Test
void maxHomeSlotsIncreasesByUpgradeLevel() {
    // Arrange: player has upgrade level 2, base slots = 3, slots-per-level = 2
    // Act: resolve max homes for this player
    // Assert: result == 3 + (2 * 2) == 7
}

@Test
void maxHomeSlotsIsBaseWhenUpgradeLevel0() {
    // Arrange: player has upgrade level 0, base slots = 3
    // Act: resolve max homes
    // Assert: result == 3
}

@Test
void maxHomeSlotsIsBaseWhenUpgradeServiceNull() {
    // Arrange: no upgrade service available
    // Assert: falls back to base slot count
}
```

Run the test to confirm it fails.

- [ ] **Step 3: Add HavenUpgradeService to the limit resolver**

In the class that resolves max slots (identified in Step 1):
- Add an `@Nullable HavenUpgradeService upgradeService` field with a setter (or pass via constructor if the class already does DI via constructor)
- Add config values for base slots and per-level bonus:
  - `slots.homes.base: 3` and `slots.homes.per-level: 2`
  - `slots.warps.base: 2` and `slots.warps.per-level: 1`
  - `slots.shops.base: 1` and `slots.shops.per-level: 1`
- In the max-home resolver method:
  ```java
  int base = config.getInt("slots.homes.base", 3);
  int perLevel = config.getInt("slots.homes.per-level", 2);
  int level = upgradeService != null
      ? upgradeService.currentLevel(playerId, "home-slots") : 0;
  return base + (level * perLevel);
  ```
- Apply the same pattern for warps (`warp-slots`) and shops (`shop-slots`)

- [ ] **Step 4: Add slot config to config.yml**

```yaml
slots:
  homes:
    base: 3
    per-level: 2
  warps:
    base: 2
    per-level: 1
  shops:
    base: 1
    per-level: 1
```

- [ ] **Step 5: Run tests**

```
./gradlew test --tests "com.nick.teleportlocations.limit.*"
```

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```
git add src/main/java/com/nick/teleportlocations/limit/
git add src/main/resources/config.yml
git add src/test/java/com/nick/teleportlocations/limit/
git commit -m "feat: home/warp/shop slot limits driven by upgrade level"
```

---

## Task 3: Warmup Action Bar Animation

**Files:**
- Read: `src/main/java/com/nick/teleportlocations/tpa/TeleportWarmupService.java`
- Modify: `src/main/java/com/nick/teleportlocations/tpa/TeleportWarmupService.java`

The existing warmup service already handles cancellation on move/quit and scheduled callbacks. We need to add an action bar progress animation for the duration of the countdown.

- [ ] **Step 1: Read TeleportWarmupService.java**

Read the file. Identify:
- What triggers warmup start? (`begin()` or similar method?)
- How is the duration passed in?
- How is cancellation currently communicated?
- Does it already use a repeating task, or a single delayed task?

- [ ] **Step 2: Write failing tests**

Write tests that verify:

```java
@Test
void actionBarUpdatedDuringWarmup() {
    // Arrange: mock Player, start a warmup
    // Act: advance time by one tick via scheduler mock
    // Assert: player.sendActionBar() was called at least once
}

@Test
void actionBarClearedOnCancel() {
    // Arrange: active warmup, then cancel
    // Assert: player.sendActionBar(Component.empty()) called
}

@Test
void actionBarClearedOnComplete() {
    // Arrange: warmup completes (onComplete callback fires)
    // Assert: player.sendActionBar(Component.empty()) called
}
```

Run tests to confirm they fail.

- [ ] **Step 3: Add action bar animation**

In `TeleportWarmupService`, add or modify the warmup task to:

1. On warmup start, record `startTimeMs = System.currentTimeMillis()` and `durationMs = durationSeconds * 1000L`
2. Add (or modify existing) repeating task every 2 ticks:

```java
int barWidth = 10; // configurable
long elapsed = System.currentTimeMillis() - startTimeMs;
double progress = Math.min(1.0, (double) elapsed / durationMs);
int filled = (int) (progress * barWidth);
String filledChar = config.getString("warmup.bar.filled-char", "█");
String emptyChar  = config.getString("warmup.bar.empty-char", "░");
String bar = filledChar.repeat(filled) + emptyChar.repeat(barWidth - filled);
long secondsLeft = Math.max(0, (durationMs - elapsed) / 1000);
Component actionBar = MiniMessage.miniMessage().deserialize(
    "<yellow>Teleporting " + bar + " " + secondsLeft + "s");
player.sendActionBar(actionBar);
```

3. On complete or cancel: `player.sendActionBar(Component.empty())` and send cancel message if cancelled:

```java
String cancelMsg = config.getString("warmup.cancel-message", "<red>Teleport cancelled.");
player.sendMessage(MiniMessage.miniMessage().deserialize(cancelMsg));
```

4. Players with bypass permission skip warmup entirely — no action bar. Bypass permission:  
   `haventeleport.warmup.bypass` (global) or per-command `haventeleport.warmup.bypass.<commandKey>`.  
   Check this in the code path that starts warmup; if player has bypass, call `onComplete` immediately.

- [ ] **Step 4: Add warmup config to config.yml**

```yaml
warmup:
  cancel-message: "<red>Teleport cancelled. You moved!"
  bar:
    filled-char: "█"
    empty-char: "░"
    width: 10
```

- [ ] **Step 5: Run tests**

```
./gradlew test --tests "com.nick.teleportlocations.tpa.*"
```

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```
git add src/main/java/com/nick/teleportlocations/tpa/TeleportWarmupService.java
git add src/main/resources/config.yml
git add src/test/java/com/nick/teleportlocations/tpa/
git commit -m "feat: action bar progress animation during teleport warmup"
```

---

## Task 4: /homes Dialog Redesign

**Files:**
- Read: `src/main/java/com/nick/teleportlocations/dialog/DialogMenuService.java`
- Read: `src/main/java/com/nick/teleportlocations/dialog/DialogActionRouter.java`
- Read: `src/main/java/com/nick/teleportlocations/dialog/DialogActionExecutor.java`
- Read: `src/main/java/com/nick/teleportlocations/dialog/PaperDialogPresenter.java`
- Modify: all four files above

### Goal

**Main `/homes` dialog:**
- Body shows: `Homes (used/max)` e.g. `Homes (3/5)`
- One button per home: label = `★ name` if main home, else `name`; tooltip = world + coords
- `+ Add Home` button always present:
  - At cap → no-op action + `"Home slots full. Upgrade in /upgrades."`
  - Below cap → closes dialog, executes set-home at player's current location with an auto-generated name (`home-1`, `home-2`, etc. — first available slot name not already taken)
- 2-column layout

**Per-home sub-dialog (replaces "Edit" flow):**
- Title: home name
- Body: world + coordinates plain text
- Buttons: `Teleport` · `Set as Main` (hidden if already main) · `Delete`  
- `← Back` button returns to main homes list

- [ ] **Step 1: Read the four dialog files**

Read all four. Understand:
- What model type does `homesMenu()` return?
- How does `PaperDialogPresenter` convert that model to Paper Dialog API?
- What action key format does `DialogActionRouter` use? (e.g., `"teleport:home:name"`)
- How does `DialogActionExecutor.handle()` process those keys?
- What existing action keys exist for homes? Are any used for the old "Edit" sub-dialog?

- [ ] **Step 2: Write failing tests**

Write tests that verify the new menu structure:

```java
@Test
void mainDialogBodyShowsSlotCount() {
    // Arrange: player has 2 homes, max = 5 (from limit system)
    // Act: build homesMenu
    // Assert: body contains "Homes (2/5)"
}

@Test
void mainHomeButtonHasStarPrefix() {
    // Arrange: one home marked as mainHome=true, name="castle"
    // Act: build homesMenu
    // Assert: button label contains "★ castle" or "★castle"
}

@Test
void nonMainHomeButtonHasNoStar() {
    // Arrange: home marked as mainHome=false, name="mine"
    // Act: build homesMenu
    // Assert: button label is "mine" (no ★)
}

@Test
void addHomeButtonAlwaysPresent() {
    // Arrange: player has 0 homes
    // Act: build homesMenu
    // Assert: one button with label containing "Add Home" or "+"
}
```

Run to confirm they fail.

- [ ] **Step 3: Rewrite homesMenu() in DialogMenuService**

Replace the `homesMenu()` method body. This shows the conceptual change — adapt to the actual model types used:

```java
public DialogMenuModel homesMenu(UUID viewerId, List<TeleportLocation> homes, int maxSlots) {
    // Body: slot count
    String body = "Homes (" + homes.size() + "/" + maxSlots + ")";

    List<DialogActionModel> actions = new ArrayList<>();

    // One button per home
    for (TeleportLocation home : homes) {
        String label = home.mainHome() ? "★ " + home.name() : home.name();
        String tooltip = formatPosition(home.position()); // e.g. "world: 100, 64, -200"
        actions.add(new DialogActionModel("view:home:" + home.normalizedName(), label, tooltip));
    }

    // Add Home button
    boolean atCap = homes.size() >= maxSlots;
    String addLabel = "+ Add Home";
    String addAction = atCap ? "homes:cap" : "homes:add";
    actions.add(new DialogActionModel(addAction, addLabel, null));

    return new DialogMenuModel("Homes", List.of(body), List.copyOf(actions));
}

private String formatPosition(SavedPosition pos) {
    return pos.worldName() + " " + pos.x() + ", " + pos.y() + ", " + pos.z();
}
```

**Note:** `DialogActionModel` may have different constructors — adapt to actual signature (no tooltip param? add it or use a separate display string).

- [ ] **Step 4: Add homeSubMenu() to DialogMenuService**

Add a new method for the per-home sub-dialog:

```java
public DialogMenuModel homeSubMenu(TeleportLocation home) {
    String body = formatPosition(home.position());

    List<DialogActionModel> actions = new ArrayList<>();
    actions.add(new DialogActionModel("teleport:home:" + home.normalizedName(), "Teleport", null));
    if (!home.mainHome()) {
        actions.add(new DialogActionModel("set-main:home:" + home.normalizedName(), "Set as Main", null));
    }
    actions.add(new DialogActionModel("delete:home:" + home.normalizedName(), "Delete", null));
    actions.add(new DialogActionModel("homes:back", "← Back", null));

    return new DialogMenuModel(home.name(), List.of(body), List.copyOf(actions));
}
```

- [ ] **Step 5: Update DialogActionRouter to handle new action keys**

Add handlers for:
- `"view:home:<name>"` → open `homeSubMenu(home)` for the named home
- `"homes:back"` → reopen the main homes list
- `"homes:add"` → close dialog, execute set-home at current location with auto-name
- `"homes:cap"` → send message `"Home slots full. Upgrade in /upgrades."` without teleporting

These should be added to the switch statement in `DialogActionRouter` (or wherever action routing happens).

- [ ] **Step 6: Update DialogActionExecutor for new actions**

Wire the new action keys to actual behavior:

- `homes:back` → call `presenter.show(player, dialogs.homesMenu(player.getUniqueId(), homes.listHomes(player.getUniqueId()), limitService.maxHomes(player.getUniqueId())))`
- `homes:add` → close dialog, then:
  ```java
  String autoName = nextAvailableHomeName(player.getUniqueId()); // "home-1", "home-2" etc.
  homeService.setHome(player, autoName); // adapt to actual API
  ```
- `homes:cap` → `player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Home slots full. Upgrade via <click:run_command:'/upgrades'>/upgrades</click>."))`
- `view:home:<name>` → `presenter.show(player, dialogs.homeSubMenu(home))`

- [ ] **Step 7: Update /homes command to pass max slots**

In `PlayerLocationCommand`, the `/homes` case currently calls:
```java
presenter.show(player, dialogs.homesMenu(player.getUniqueId(), homes.listHomes(player.getUniqueId())));
```

Update to:
```java
int maxHomes = limitService.maxHomes(player.getUniqueId()); // use the limit service you modified in Task 2
presenter.show(player, dialogs.homesMenu(player.getUniqueId(), homes.listHomes(player.getUniqueId()), maxHomes));
```

- [ ] **Step 8: Run tests**

```
./gradlew test --tests "com.nick.teleportlocations.dialog.*"
```

Expected: all tests PASS.

- [ ] **Step 9: Commit**

```
git add src/main/java/com/nick/teleportlocations/dialog/
git add src/main/java/com/nick/teleportlocations/command/
git add src/test/java/com/nick/teleportlocations/dialog/
git commit -m "feat: redesigned /homes dialog with slot count and per-home sub-dialog"
```

---

## Task 5: Wire into TeleportLocationsPlugin

**Files:**
- Read: `src/main/java/com/nick/teleportlocations/TeleportLocationsPlugin.java`
- Read: `src/main/java/com/nick/teleportlocations/RuntimeServices.java`
- Modify: `TeleportLocationsPlugin.java`
- Modify: `RuntimeServices.java`

- [ ] **Step 1: Read both files**

Identify:
- How does the plugin currently get `HavenUpgradeService`? (Via Bukkit ServicesManager: `getServer().getServicesManager().load(HavenUpgradeService.class)`)
- Where does `onEnable()` register event listeners?
- How is `RuntimeServices` constructed and used?

- [ ] **Step 2: Create and register TeleportUpgradeProvider**

In `TeleportLocationsPlugin.onEnable()`, after resolving the upgrade service from Bukkit ServicesManager:

```java
HavenUpgradeService upgradeService = getServer().getServicesManager().load(HavenUpgradeService.class);
HavenEconomyService economyService = getServer().getServicesManager().load(HavenEconomyService.class);

if (upgradeService != null) {
    TeleportUpgradeProvider teleportProvider = new TeleportUpgradeProvider(getConfig(), economyService);
    upgradeService.registerProvider(teleportProvider);
    getLogger().info("Registered TeleportUpgradeProvider with HavenCore.");
} else {
    getLogger().warning("HavenUpgradeService not found — upgrade slots disabled.");
}
```

Unregister in `onDisable()`:
```java
HavenUpgradeService upgradeService = getServer().getServicesManager().load(HavenUpgradeService.class);
if (upgradeService != null) {
    upgradeService.unregisterProvider(TeleportUpgradeProvider.PROVIDER_ID);
}
```

- [ ] **Step 3: Inject upgradeService into the limit resolver**

Pass `upgradeService` to the limit system (wherever you added the field in Task 2).

- [ ] **Step 4: Run full test suite**

```
./gradlew test
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/nick/teleportlocations/TeleportLocationsPlugin.java
git add src/main/java/com/nick/teleportlocations/RuntimeServices.java
git commit -m "feat: wire TeleportUpgradeProvider and upgrade-aware slot limits"
```

- [ ] **Step 6: Push**

```
git push
```

---

## Verification checklist

After all tasks complete, test in-game:

- [ ] `/upgrades` shows Personal category with "Home Slots I-V", "Warp Slots I-V", "Shop Slots I-V" alongside any HavenCore upgrades
- [ ] Purchase "Home Slots I" — `/homes` now shows `Homes (X/5)` where 5 = base 3 + level-1 bonus 2
- [ ] `/homes` main dialog: main home has ★ prefix, non-main homes do not
- [ ] Clicking a home opens sub-dialog with Teleport, (Set as Main if not main), Delete, ← Back
- [ ] `← Back` returns to the main homes list
- [ ] `+ Add Home` at cap sends upgrade message instead of setting a home
- [ ] `+ Add Home` below cap closes dialog and sets home at current location
- [ ] During `/home` teleport: action bar shows `Teleporting ██████░░░░ 3s` countdown, cancels on move, clears on arrival
- [ ] Players with `haventeleport.warmup.bypass` permission teleport instantly with no action bar
