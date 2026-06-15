# HavenCore Features Design: Chat Channels, AFK, Tab Layout, Custom Messages

**Date:** 2026-06-14  
**Status:** Approved  
**Scope:** Four new feature modules added to `haven-core`, with API contracts added to `haven-api`.

---

## 1. Overview

Four independent feature modules, each with its own config file and feature toggle, wired up in `HavenCore.onEnable()`:

| Feature | Config file | Toggle key |
|---|---|---|
| Chat channels + market | `chat.yml` | `features.chat-formatting` |
| AFK detection | `afk.yml` | `features.afk` |
| Tab layout | `tab.yml` | `features.tab-list` |
| Custom messages | `messages.yml` | `features.custom-messages` |

**Shared infrastructure:**
- `GroupResolver` — resolves LuckPerms primary group for a player; falls back to `"default"` if LuckPerms absent. Used by `ChatChannelService` and `TabManager`.
- `HavenExpansion extends PlaceholderExpansion` — single PAPI expansion class handling all `%haven_*%` placeholders. Registered in `HavenCore.onEnable()` when `PlaceholderAPIHook` is available.
- Four new `features:` toggles added to `config.yml`.

---

## 2. Chat Channel System

### 2.1 Goals

- Per-LuckPerms-group chat formatting with MiniMessage + PAPI support.
- Channel routing: global, staff, claim-local (future), and market.
- Market channel: command-driven advertisement queue with clickable shop warp links.
- Extensible to private channels, local radius channels, and HavenClaims integration without code changes.

### 2.2 `haven-api` additions

```
haven-api/chat/
  ChatRecipientType.java     enum ALL | PERMISSION | RADIUS | CLAIM
  ChatChannel.java           record: id, displayName, format, recipientType,
                               permission, radiusBlocks, cooldownSeconds,
                               triggerPrefix, isDefault
  HavenChatService.java      interface
  HavenWarpService.java      interface (stub — implemented by HavenWarps later)
```

**`HavenChatService`:**
```java
Optional<ChatChannel> getChannel(String id);
ChatChannel getPlayerChannel(UUID uuid);          // returns default if unset
void setPlayerChannel(UUID uuid, String channelId);
void sendToChannel(Player sender, ChatChannel channel, String rawMessage);
```

**`HavenWarpService`:**
```java
List<String> getShopWarps(UUID playerUuid);       // returns empty list until HavenWarps present
boolean hasShopWarp(UUID playerUuid, String name);
```

### 2.3 `haven-core` classes

```
haven-core/chat/
  ChatSettings.java           parses chat.yml; builds channel map + market config
  ChatChannelService.java     implements HavenChatService
  ChatChannelListener.java    AsyncChatEvent → routes to player's active channel
  MarketQueue.java            FIFO queue + BukkitRunnable slot-interval broadcast
  MarketCommand.java          /market advertise [shopwarp] [message]
  ChannelCommand.java         /channel <id>
```

**`ChatChannelListener`:**
- Listens at `EventPriority.HIGHEST` with `ignoreCancelled = true`.
- Cancels the original event; delegates to `ChatChannelService.sendToChannel()`.
- Resolves trigger-prefix channels first (e.g. message starting with `@` → staff channel), then falls back to player's active channel.

**`ChatChannelService` routing logic:**
- `ALL` — broadcasts to all online players.
- `PERMISSION` — broadcasts to players with the channel's `permission` node.
- `RADIUS` — broadcasts to players within `radiusBlocks` of sender (same world).
- `CLAIM` — placeholder: logs warning and falls back to `ALL` until `HavenClaimsService` is registered.
- Applies PAPI placeholders via `PlaceholderAPIHook` before MiniMessage deserialization.

**`MarketCommand`:**
- `/market advertise <shopwarp> <message>`
- Validates `HavenWarpService.hasShopWarp(uuid, shopwarp)` — rejects with error if false or warp service absent.
- Checks `HavenCooldownService` for per-player submission cooldown.
- Enqueues `AdvertisementRequest(uuid, playerName, shopwarp, message, submitTime)`.
- Tab-completer calls `HavenWarpService.getShopWarps(uuid)`.

**`MarketQueue`:**
- `ArrayDeque<AdvertisementRequest>` protected by `synchronized`.
- `BukkitRunnable` fires every `slot-interval` seconds, pops the front entry, broadcasts to all online players not in `"market-muted"` player data.
- Shopwarp token rendered as a `Component` with `ClickEvent.runCommand(warpCommand.formatted(shopwarp))` and a `HoverEvent.showText(...)` tooltip. Not a plain string replacement.

**`ChannelCommand`:**
- `/channel <id>` — validates channel exists and player has permission to use it.
- Stores choice in `HavenPlayer.getData("haven-core", "active-channel")`.

### 2.4 `chat.yml`

```yaml
channels:
  global:
    display-name: "Global"
    format: "[%haven_rank_prefix%] <player_name>: <message>"
    recipient-type: ALL
    default: true
  staff:
    display-name: "Staff"
    format: "<red>[Staff]</red> <player_name>: <message>"
    recipient-type: PERMISSION
    permission: "haven.staff"
    trigger-prefix: "@"

market:
  slot-interval: 300
  player-cooldown: 600
  format: "<gold>[Market]</gold> <player_name> @ <shopwarp>: <message>"
  warp-command: "/warp %s"
  warp-hover: "<yellow>Click to visit %s!"
```

### 2.5 Permissions

```
haven.channel.<id>      — use a specific channel (checked if channel has no permission field)
haven.staff             — send/receive staff channel
haven.market.advertise  — submit market advertisements
haven.market.mute       — /market mute (toggle own market broadcast opt-out)
haven.admin.channel     — /haven channel admin subcommands
```

---

## 3. AFK Detection System

### 3.1 Goals

- Detect idle players via configurable activity events.
- Resist passive evasion (water traps, pistons, portals, bubble columns).
- Flag suspicious scripted evasion to admins.
- Expose AFK state via PAPI and `HavenAfkService`.
- Broadcast opt-out per player; action bar warning to AFK player; auto-kick.

### 3.2 `haven-api` additions

```
haven-api/service/
  HavenAfkService.java
```

```java
boolean isAfk(UUID uuid);
long getIdleSeconds(UUID uuid);          // 0 if not online or not tracked
void setAfk(UUID uuid, boolean afk);    // admin override
```

### 3.3 `haven-core` classes

```
haven-core/afk/
  AfkSettings.java
  AfkManager.java     implements HavenAfkService + Listener
  AfkCommand.java     /afk, /afk notifications
```

**`AfkManager` detail:**

State:
```java
ConcurrentHashMap<UUID, Long>    lastActivity;   // epoch ms
ConcurrentHashMap<UUID, Boolean> afkState;
```

Activity events (each individually configurable):
- `PlayerMoveEvent` — only resets timer if `strict-movement: true` AND (`event.hasChangedOrientation()` OR minimum rotation delta exceeded). With `strict-movement: false`, any position change counts.
- `PlayerInputEvent` — keyboard/mouse input packets; most evasion-resistant signal.
- `AsyncChatEvent` (Paper) — chat.
- `PlayerCommandPreprocessEvent` — commands.
- `PlayerInteractEvent` — block/entity interact.
- `PlayerTeleportEvent` with cause `NETHER_PORTAL | END_PORTAL | PLUGIN` — explicitly **excluded** from resetting timer regardless of settings.

Scheduler (BukkitRunnable, every 5s):
1. For each online player not already AFK: if `System.currentTimeMillis() - lastActivity > timeout * 1000` → mark AFK.
2. For each AFK player: if `kick-timeout > 0` and idle > kick-timeout → kick.
3. Pattern detection: if `pattern-alert: true`, flag players whose activity resets show suspiciously regular intervals (< 200ms standard deviation over last 10 resets) → broadcast to `haven.afk.alerts`.

On AFK:
- Broadcasts `afk-broadcast` message to online players excluding those with `HavenPlayer.getData("haven-core", "afk-broadcast-muted") = "true"`.
- Starts repeating action bar task to AFK player.
- Notifies `TabManager` to refresh that player's display name (if tab-list feature enabled).
- Calls `PlayerMessageService.getAfkMessage(player)` for broadcast text if custom-messages feature enabled; otherwise falls back to `afk.yml` message.

On return:
- Clears action bar.
- Broadcasts return message (same opt-out logic).
- Notifies `TabManager`.

**`AfkCommand`:**
- `/afk` — manually toggle AFK state (permission: `haven.afk.manual`).
- `/afk notifications` — toggles `"afk-broadcast-muted"` in `HavenPlayer` data.

### 3.4 `afk.yml`

```yaml
timeout: 300
kick-timeout: 1800

activity-events:
  movement: true
  keyboard-input: true
  chat: true
  commands: true
  interact: true

strict-movement: true
detection:
  min-rotation-delta: 1.5
  pattern-alert: true
  pattern-alert-permission: "haven.afk.alerts"

messages:
  afk-broadcast: "<gray><player> is now AFK."
  return-broadcast: "<gray><player> is no longer AFK."
  action-bar: "<yellow>You are AFK. Move to return."
  kick-reason: "You were kicked for being AFK."
```

### 3.5 Limitations

`PlayerMoveEvent`, `PlayerInputEvent`, and all other activity signals are derived from client-sent packets. A modded client can send crafted packets to bypass detection. This system defeats passive evasion (server-forced movement) and makes scripted evasion significantly harder via the rotation delta threshold and pattern detection. It is not a substitute for a dedicated anti-cheat plugin.

### 3.6 Permissions

```
haven.afk.manual        — /afk to manually toggle
haven.afk.alerts        — receive suspicious pattern alerts
haven.admin.afk         — /haven afk admin subcommands (force-set AFK state)
```

---

## 4. Tab Layout

### 4.1 Goals

- Configurable header/footer with MiniMessage + PAPI (refreshed on interval for dynamic values).
- Per-LuckPerms-group player display name format in the tab list.
- AFK override format applied immediately when AFK state changes.

### 4.2 `haven-core` classes

```
haven-core/tab/
  TabSettings.java
  TabManager.java     Listener + BukkitRunnable
```

**`TabManager` detail:**

- `onJoin(PlayerJoinEvent)` — sends header/footer to joining player; updates that player's display name for all online players.
- `onQuit(PlayerQuitEvent)` — restores player's display name to default.
- `BukkitRunnable` (every `refresh-interval` seconds) — resends header/footer to all online players. Necessary for dynamic PAPI placeholders like `%server_online%`.
- `refreshPlayer(Player)` — resolves group format via `GroupResolver`, applies AFK override if `HavenAfkService.isAfk(uuid)`. Called by `AfkManager` on state change and by the scheduler.
- Header/footer lines joined with `\n` into a single `Component` via MiniMessage. PAPI resolved per-player if `PlaceholderAPIHook` available.

### 4.3 `tab.yml`

```yaml
enabled: true
refresh-interval: 5

header:
  - "<gradient:aqua:blue>✦ Haven SMP ✦</gradient>"
  - "<gray>discord.gg/haven"

footer:
  - "<gray>%server_online% / %server_max_players% online"

player-format:
  default: "<white><player_name>"
  groups:
    owner: "<gold>[Owner] <player_name>"
    admin: "<dark_purple>[Admin] <player_name>"
    vip: "<green>[VIP] <player_name>"

afk-format: "<gray>[AFK] <player_name>"
```

---

## 5. Custom Messages System

### 5.1 Goals

- Players select from unlocked presets for join, quit, and AFK messages.
- Permission-gated custom messages via `/joinmsg`, `/quitmsg`, `/afkmsg`.
- Presets unlocked via codex milestones, admin grant, or permission node.
- Death messages: random selection per damage cause, premium pool gated by permission.
- Content filter: MiniMessage tag allowlist + configurable regex blocklist.

### 5.2 `haven-api` additions

```
haven-api/service/
  HavenMessageService.java
```

```java
Component getJoinMessage(Player player);
Component getQuitMessage(Player player);
Component getAfkMessage(Player player);
Component getDeathMessage(Player player, DamageCause cause, @Nullable Entity killer);
void unlockPreset(UUID uuid, String presetId);
List<String> getUnlockedPresets(UUID uuid);
```

### 5.3 `haven-core` classes

```
haven-core/message/
  MessageSettings.java
  PlayerMessageService.java     implements HavenMessageService + Listener (codex events)
  PlayerMessageListener.java    PlayerJoinEvent, PlayerQuitEvent, PlayerDeathEvent
  MiniMessageSanitizer.java
  JoinMsgCommand.java
  QuitMsgCommand.java
  AfkMsgCommand.java
```

**Message resolution priority (join/quit/afk):**
1. Player has `"<type>-msg-type" = "custom"` in HavenPlayer data → deserialize `"<type>-msg-custom"` through `MiniMessageSanitizer`.
2. Player has `"<type>-msg-type" = "preset"` → look up `"<type>-msg-preset"` ID in `MessageSettings`, return its `message`.
3. Fallback → server default preset (marked `default: true` in config).

**`PlayerMessageListener`:**
- `PlayerJoinEvent` — sets `event.joinMessage(null)`, broadcasts `getJoinMessage(player)` to all online players.
- `PlayerQuitEvent` — sets `event.quitMessage(null)`, broadcasts `getQuitMessage(player)`.
- `PlayerDeathEvent` — sets `event.deathMessage(null)`, broadcasts `getDeathMessage(player, cause, killer)`.
- AFK broadcast is handled by `AfkManager` calling `PlayerMessageService.getAfkMessage(player)` directly.

**`PlayerMessageService` codex integration:**
- Listens to `HavenCodexMilestoneEvent`. When fired, checks all presets with `unlock-type: CODEX` for matching `codex-milestone`. Grants matching preset IDs to the player automatically.
- Note: codex integration requires HavenCodex milestone event infrastructure to be extended. This is deferred to a separate spec.

**Market opt-out storage in `HavenPlayer`:**
```
"market-muted"         → "true" | absent (false)
```
Toggled via `/market mute`. Checked in `MarketQueue` before broadcasting each advertisement to a player.

**Unlock storage in `HavenPlayer`:**
```
"unlocked-presets"     → comma-separated preset IDs e.g. "wave,chicken-slayer"
"join-msg-type"        → "custom" | "preset" | absent (use default)
"join-msg-custom"      → raw MiniMessage string (sanitized on write)
"join-msg-preset"      → preset ID
"quit-msg-type"        → same pattern
"quit-msg-custom"
"quit-msg-preset"
"afk-msg-type"         → same pattern
"afk-msg-custom"
"afk-msg-preset"
```

**`MiniMessageSanitizer`:**
- Allowed tags: `color`, `gradient`, `rainbow`, `bold`, `italic`, `underlined`, `strikethrough`, `obfuscated`, `reset`, `newline`, named colors (e.g. `<red>`).
- Blocked tags: `click`, `hover`, `insertion`, `font`, `transition`, `selector`, `nbt`, `score`.
- After tag stripping: runs each `blocked-patterns` regex against the plain text. Rejects with error if match found.
- Players with `haven.messages.bypass-filter` skip sanitizer entirely.

**Death message resolution:**
1. Look up messages list for the exact `DamageCause`.
2. Falls back to `CUSTOM` cause pool if no entry found, then `UNKNOWN` pool.
3. Filters to messages where `permission` field is absent OR player has that permission.
4. Picks randomly from filtered set using `ThreadLocalRandom`.

**Commands:**
```
/joinmsg set <message>         haven.messages.custom
/joinmsg select <preset-id>    (preset must be unlocked)
/joinmsg clear
/quitmsg set|select|clear      same
/afkmsg set|select|clear       same
/haven messages unlock <player> <preset-id>    haven.admin.messages
```

### 5.4 `messages.yml`

```yaml
filter:
  max-length: 100
  blocked-patterns:
    - "(?i)(example_slur)"
    - "(?i)https?://"

join-presets:
  default:
    message: "<green>→ <player> joined."
    unlock-type: FREE
  chicken-slayer:
    message: "<yellow><player>, the Chicken Slayer, has arrived."
    unlock-type: CODEX
    codex-milestone: "chicken_slayer_5"
  royal:
    message: "<gold>✦ <player> has graced us with their presence."
    unlock-type: PERMISSION
    permission: "haven.messages.preset.royal"

quit-presets:
  default:
    message: "<gray>← <player> left."
    unlock-type: FREE

afk-presets:
  default:
    message: "<gray><player> is now AFK."
    unlock-type: FREE

death-messages:
  ENTITY_ATTACK:
    - message: "<player> was slain by <killer>."
    - message: "<player> got destroyed by <killer>."
    - message: "<player> found out why <killer> is dangerous."
      permission: "haven.messages.premium"
  PROJECTILE:
    - message: "<player> was shot by <killer>."
    - message: "<player> couldn't dodge <killer>'s shot."
  FALL:
    - message: "<player> hit the ground too hard."
    - message: "<player> mistook gravity for a suggestion."
    - message: "<player> forgot that falling hurts."
      permission: "haven.messages.premium"
  DROWNING:
    - message: "<player> forgot they weren't a fish."
    - message: "<player> drowned."
  FIRE:
    - message: "<player> went up in flames."
    - message: "<player> burned to a crisp."
  FIRE_TICK:
    - message: "<player> burned to death."
  LAVA:
    - message: "<player> tried to swim in lava."
    - message: "<player> discovered lava is not a pool."
  MAGIC:
    - message: "<player> was killed by magic."
  POISON:
    - message: "<player> was poisoned to death."
  STARVATION:
    - message: "<player> starved to death."
  LIGHTNING:
    - message: "<player> was struck by lightning."
    - message: "<player> was chosen by the gods."
      permission: "haven.messages.premium"
  SUFFOCATION:
    - message: "<player> suffocated in a wall."
  VOID:
    - message: "<player> fell into the void."
    - message: "<player> found the bottom of the world."
  WITHER:
    - message: "<player> withered away."
  ENTITY_EXPLOSION:
    - message: "<player> blew up."
    - message: "<player> was at the wrong place at the wrong time."
  BLOCK_EXPLOSION:
    - message: "<player> was caught in an explosion."
  CONTACT:
    - message: "<player> was poked to death."
  CRAMMING:
    - message: "<player> was squished."
  FREEZE:
    - message: "<player> froze to death."
  SONIC_BOOM:
    - message: "<player> was obliterated by a sonic boom."
    - message: "<player> angered the Warden."
      permission: "haven.messages.premium"
  THORNS:
    - message: "<player> was killed by thorns."
  DRAGON_BREATH:
    - message: "<player> was consumed by dragon's breath."
  FLY_INTO_WALL:
    - message: "<player> flew into a wall."
    - message: "<player> forgot elytra have limits."
  HOT_FLOOR:
    - message: "<player> discovered the floor was lava."
  KILL:
    - message: "<player> was killed."
  UNKNOWN:
    - message: "<player> died."
    - message: "<player> met an untimely end."
```

### 5.5 Permissions

```
haven.messages.custom           — /joinmsg|quitmsg|afkmsg set
haven.messages.bypass-filter    — skip MiniMessage sanitizer and regex filter
haven.messages.premium          — access premium death message pool
haven.admin.messages            — /haven messages unlock <player> <preset-id>
```

---

## 6. PAPI Expansion (`HavenExpansion`)

Single `PlaceholderExpansion` subclass registered when `PlaceholderAPIHook.isAvailable()`. Identifier: `haven`.

| Placeholder | Returns |
|---|---|
| `%haven_afk%` | `"true"` / `"false"` |
| `%haven_afk_time%` | idle seconds as string |
| `%haven_rank_prefix%` | LuckPerms prefix via `GroupResolver`, empty string if absent |
| `%haven_rank_group%` | LuckPerms primary group name, `"default"` if absent |

---

## 7. Wiring in `HavenCore.onEnable()`

Order of initialization:
1. `AfkSettings`, `AfkManager` — registered as `HavenAfkService` with Bukkit ServicesManager.
2. `MessageSettings`, `PlayerMessageService` — registered as `HavenMessageService`. After construction, call `AfkManager.setMessageService(playerMessageService)` to resolve the forward reference — `AfkManager` holds a nullable `HavenMessageService` field and falls back to `afk.yml` messages when it is null or when `features.custom-messages` is disabled.
3. `TabSettings`, `TabManager` — receives reference to `AfkManager` for state queries.
4. `ChatSettings`, `ChatChannelService`, `ChatChannelListener`, `MarketQueue` — `ChatChannelService` registered as `HavenChatService`.
5. `HavenExpansion` — registered with PlaceholderAPI if hook available.
6. New commands registered: `/channel`, `/market`, `/afk`, `/joinmsg`, `/quitmsg`, `/afkmsg`.
7. New feature toggles in `config.yml` checked; each module skipped if its toggle is `false`.

`ConfigManager` extended to load `chat.yml`, `afk.yml`, `tab.yml`, `messages.yml` following existing pattern.

---

## 8. Deferred / Out of Scope

- **Claim-local channel** — `ChatRecipientType.CLAIM` is defined; routing degrades to `ALL` with a logged warning until `HavenClaimsService` is registered as a Bukkit service.
- **Codex milestone unlocks for presets** — `HavenCodexMilestoneEvent` listener exists in `PlayerMessageService` but requires HavenCodex to fire milestone events with stable IDs. Addressed in a separate HavenCodex spec.
- **HavenWarpService implementation** — interface defined in `haven-api`; stub returns empty list until HavenWarps plugin implements and registers it.
- **Private/DM channels** — `ChatRecipientType` supports extension; no implementation in this spec.
- **Market shop linking** — clickable warp links are implemented; deep shop inventory linking deferred.
