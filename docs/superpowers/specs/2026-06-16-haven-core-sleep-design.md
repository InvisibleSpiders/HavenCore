# HavenCore Sleep Module Design

**Date:** 2026-06-16
**Status:** Approved
**Scope:** Sleep-to-skip-night feature with dynamic speed scaling, AFK/spectator exclusion, bypass permission, pause-on-threshold-loss, and admin commands.

---

## 1. Overview

A sleep module that lets players skip the night by sleeping in beds. Night skip uses a smooth fast-forward animation (time advances rapidly each tick so the sky visually sweeps to dawn). The speed of the skip scales with how many active players are sleeping. AFK players, spectators, and players with a bypass permission are excluded from all calculations. The module is feature-toggled and registers a `HavenSleepService` to the Bukkit ServicesManager so other Haven plugins can query sleep state.

---

## 2. Active Player Definition

For a given world, an **active player** is any online player who:
- Is currently in that world
- Is not in `SPECTATOR` game mode
- Does not have `haven.sleep.bypass` permission
- Is not AFK (`HavenAfkService.isAfk(uuid) == false`)

Only active players count toward threshold and speed calculations. Players in other worlds (e.g. Nether, End) are invisible to the overworld sleep system.

---

## 3. Per-World State Machine

Each eligible world tracks one of four internal states:

| State | Description |
|---|---|
| `IDLE` | No one sleeping; normal time progression |
| `WAITING` | Players sleeping but below threshold |
| `SKIPPING` | At or above threshold; `BukkitRunnable` advancing time each tick |
| `PAUSED` | Was skipping; count dropped below threshold mid-skip; time frozen at current point |

**Transitions:**
- Any bed enter/leave → recompute count → re-evaluate threshold → transition as needed
- `PlayerQuitEvent` treated as leaving bed
- AFK state changes re-evaluate active count
- PAUSED → SKIPPING resumes from current time position when threshold is met again
- SKIPPING → complete when `world.getTime() >= 23460` (dawn threshold)

---

## 4. Speed Scaling

Speed is recalculated every tick during SKIPPING state:

```
ratio  = sleepingCount / activeCount          (clamped 0.0–1.0)
speed  = lerp(min-speed, max-speed, ratio)    (game-ticks advanced per server-tick)
world.setTime(world.getTime() + speed)
```

Normal day cycle = 1 game-tick per server-tick. Default `min-speed: 40` (~12 s skip), `max-speed: 200` (~2.5 s skip).

---

## 5. Threshold

Threshold is met when **both** conditions are satisfied:

```
sleepingCount >= min-count   (0 = no minimum)
sleepingCount >= ceil(activeCount * min-percent / 100.0)   (0 = no minimum)
```

Default config sets `min-count: 1` and `min-percent: 0`, which means any single player sleeping starts the skip (Option B behavior by default, while the full C logic is always active for servers that want stricter thresholds).

---

## 6. On Skip Complete

When `world.getTime()` reaches dawn:

1. Set world time to `0` (start of day).
2. Reset `STATISTIC_TIME_SINCE_REST` to `0` for every player who slept during the skip (tracked in a `Set<UUID>` per skip).
3. Eject all sleeping players from beds via Paper API.
4. Fire `HavenSleepSkipCompleteEvent` on both Bukkit and `HavenEventBus`.
5. Transition world state → `IDLE`. Clear per-skip sleeper set.

---

## 7. `haven-api` Additions

### `HavenSleepService`

```java
public interface HavenSleepService {
    boolean isSkipping(World world);       // true only when time is actively advancing
    int getSleepingCount(World world);     // active players currently in beds
    int getActiveCount(World world);       // non-AFK, non-spectator, non-bypass players in world
}
```

### Events

```java
// Fired when WAITING/PAUSED → SKIPPING (threshold first met or re-met)
public class HavenSleepSkipStartEvent extends HavenEvent {
    World world; int sleeping; int active;
}

// Fired when skip reaches dawn
public class HavenSleepSkipCompleteEvent extends HavenEvent {
    World world;
}
```

Both fired on `HavenEventBus` and as Bukkit events.

---

## 8. `haven-core` Classes

```
haven-core/sleep/
  SleepSettings.java        parses sleep.yml
  SleepManager.java         implements HavenSleepService + Listener; owns BukkitRunnable
  SleepAdminCommand.java    /haven sleep subcommands
```

**`SleepManager` responsibilities:**
- Listens to `PlayerBedEnterEvent`, `PlayerBedLeaveEvent`, `PlayerQuitEvent`
- Maintains `Map<World, SleepState>` (internal enum, not exposed)
- Maintains `Map<World, Set<UUID>>` of sleeping players per world
- Maintains `Set<UUID>` of players who slept this skip (for insomnia reset)
- Owns the `BukkitRunnable` time-advancer; starts/stops as state transitions
- Sends action bar messages to all active players in the world each tick during SKIPPING/WAITING/PAUSED
- Sends chat broadcast on skip complete

**`SleepAdminCommand` subcommands:**
```
/haven sleep skip [world]    — force-trigger skip immediately (bypasses threshold check)
/haven sleep toggle [world]  — runtime enable/disable for a world
/haven sleep status          — print per-world state, sleeping count, active count
```

---

## 9. `sleep.yml`

```yaml
enabled: true

worlds:
  - world          # default: overworld only

threshold:
  min-count: 1     # minimum sleeping players required (0 = disabled)
  min-percent: 0   # minimum % of active players required (0 = disabled)

speed:
  min: 40          # game-ticks advanced per server-tick at lowest sleeper ratio  (~12s skip)
  max: 200         # game-ticks advanced per server-tick at 100% sleeping          (~2.5s skip)

messages:
  sleeping:       "<gray>☽ <white><sleeping></white>/<active> sleeping..."
  skip-start:     "<gold>☽ Night is being skipped!"
  skip-paused:    "<gray>☽ Skip paused — <white><sleeping></white>/<active> sleeping"
  skip-complete:  "<green>☀ Dawn breaks!"
  broadcast-skip: "<gold>☀ <gray>The night has passed."
```

Placeholders: `<sleeping>` = current sleeping count, `<active>` = active count.

---

## 10. `config.yml` Addition

```yaml
features:
  sleep: true
```

---

## 11. `plugin.yml` Additions

```yaml
permissions:
  haven.sleep.bypass:
    description: Excluded from active player count and sleep threshold checks
    default: false
  haven.admin.sleep:
    description: Manage sleep module via /haven sleep
    default: op
```

`haven.admin.sleep` added as a child of `haven.admin`.

---

## 12. Wiring in `HavenCore.onEnable()`

```java
if (configManager.getMain().getBoolean("features.sleep", true)) {
    SleepSettings sleepSettings = SleepSettings.from(configManager.getSleep());
    HavenAfkService afkService = sm.load(HavenAfkService.class); // may be null if afk disabled
    SleepManager sleepManager = new SleepManager(sleepSettings, afkService, eventBus, this, getLogger());
    getServer().getPluginManager().registerEvents(sleepManager, this);
    sm.register(HavenSleepService.class, sleepManager, this, ServicePriority.Normal);
    getLogger().info("Sleep module enabled.");
}
```

`SleepAdminCommand` constructed alongside `UpgradeAdminCommand` / `RewardAdminCommand` and routed from `HavenCommand` via the existing `haven sleep` switch case.

`ConfigManager` gets a `getSleep()` / `reloadSleep()` getter following the existing pattern.

---

## 13. Vanilla Sleep Suppression

`SleepManager.onEnable()` calls `world.setSleepingIgnored(true)` on every world in the configured whitelist. This disables vanilla's native 100%-sleep check entirely, preventing it from racing with Haven's skip. `onDisable()` restores `setSleepingIgnored(false)` on those worlds.

`PlayerBedEnterEvent` is listened to at `HIGHEST` priority to detect new sleepers. The enter animation plays normally — no cancellation needed.

---

## 14. `CoreDiagnostics` Addition

`checkRegisteredService(services, HavenSleepService.class, "sleep")` added to the `run()` results list, gated on whether the sleep feature toggle is enabled.

---

## 15. Out of Scope

- Storm clearing on sleep (deferred — separate feature).
- `/sleep` standalone player command (admin-only is sufficient for now).
- Per-player opt-out of skip broadcasts (deferred).
- Sleep-triggered codex discoveries or rewards (deferred to HavenCodex spec).
