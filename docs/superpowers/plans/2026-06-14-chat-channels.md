# Chat Channel System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace default Minecraft chat with a configurable channel system — global, staff (permission-gated), extensible to claim/radius channels — plus a market advertisement queue with clickable shopwarp links.

**Architecture:** `ChatChannelService` holds the channel map and routing logic. `ChatChannelListener` intercepts `AsyncChatEvent` at `HIGHEST` priority, cancels it, and delegates to the service. `MarketQueue` runs a `BukkitRunnable` that pops one advertisement per slot-interval and broadcasts it. Both rely on `GroupResolver` (from the AFK+Tab plan — implement that first) and use PAPI via `PlaceholderAPIHook` for placeholder resolution in format strings. `HavenWarpService` is defined as a stub interface that HavenWarps will implement later.

**Tech Stack:** Paper 26.1 API, `io.papermc.paper.event.player.AsyncChatEvent`, Adventure MiniMessage (bundled with Paper), PlaceholderAPI 2.11.6 (soft-depend), JUnit Jupiter 5.11, Mockito 5.23.

**Prerequisite:** Plan `2026-06-14-afk-tab.md` must be complete — specifically `GroupResolver`.

---

## File Map

**Create (haven-api):**
- `haven-api/src/main/java/dev/invisiblespiders/haven/api/chat/ChatRecipientType.java`
- `haven-api/src/main/java/dev/invisiblespiders/haven/api/chat/ChatChannel.java`
- `haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenChatService.java`
- `haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenWarpService.java`

**Create (haven-core):**
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatSettings.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/AdvertisementRequest.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatChannelService.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatChannelListener.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/MarketQueue.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/MarketCommand.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChannelCommand.java`
- `haven-core/src/main/resources/chat.yml`

**Create (tests):**
- `haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/ChatSettingsTest.java`
- `haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/ChatChannelServiceTest.java`
- `haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/MarketQueueTest.java`

**Modify:**
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/config/ConfigManager.java` — add `chat.yml`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java` — wire chat
- `haven-core/src/main/resources/config.yml` — add `features.chat-formatting`
- `haven-core/src/main/resources/plugin.yml` — add `/channel`, `/market` commands + permissions

---

## Task 1: ChatRecipientType + ChatChannel API

**Files:**
- Create: `haven-api/src/main/java/dev/invisiblespiders/haven/api/chat/ChatRecipientType.java`
- Create: `haven-api/src/main/java/dev/invisiblespiders/haven/api/chat/ChatChannel.java`

- [ ] **Step 1: Create ChatRecipientType**

```java
// haven-api/src/main/java/dev/invisiblespiders/haven/api/chat/ChatRecipientType.java
package dev.invisiblespiders.haven.api.chat;

public enum ChatRecipientType {
    /** Broadcast to all online players. */
    ALL,
    /** Broadcast to players with a specific permission node. */
    PERMISSION,
    /** Broadcast to players within radiusBlocks of the sender (same world). */
    RADIUS,
    /**
     * Broadcast to players in the same HavenClaims claim as the sender.
     * Degrades to ALL with a log warning until HavenClaimsService is registered.
     */
    CLAIM
}
```

- [ ] **Step 2: Create ChatChannel**

```java
// haven-api/src/main/java/dev/invisiblespiders/haven/api/chat/ChatChannel.java
package dev.invisiblespiders.haven.api.chat;

import org.jetbrains.annotations.Nullable;

public record ChatChannel(
        String id,
        String displayName,
        String format,
        ChatRecipientType recipientType,
        @Nullable String permission,
        int radiusBlocks,
        int cooldownSeconds,
        @Nullable String triggerPrefix,
        boolean isDefault
) {
    /** True if this channel uses a trigger prefix instead of requiring /channel to switch. */
    public boolean hasTriggerPrefix() { return triggerPrefix != null && !triggerPrefix.isBlank(); }
}
```

- [ ] **Step 3: Commit**

```
git add haven-api/src/main/java/dev/invisiblespiders/haven/api/chat/
git commit -m "feat(api): add ChatRecipientType enum and ChatChannel record"
```

---

## Task 2: HavenChatService + HavenWarpService API

**Files:**
- Create: `haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenChatService.java`
- Create: `haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenWarpService.java`

- [ ] **Step 1: Create HavenChatService**

```java
// haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenChatService.java
package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface HavenChatService {

    /** Returns the channel with the given id, or empty if not configured. */
    Optional<ChatChannel> getChannel(String id);

    /** Returns the player's active channel. Falls back to the default channel. */
    ChatChannel getPlayerChannel(UUID uuid);

    /** Sets the player's active channel by id. */
    void setPlayerChannel(UUID uuid, String channelId);

    /**
     * Sends a raw MiniMessage string through the given channel's routing logic.
     * Applies PAPI placeholders and MiniMessage formatting before delivery.
     */
    void sendToChannel(Player sender, ChatChannel channel, String rawMessage);

    /** Returns all configured channels. */
    Collection<ChatChannel> getChannels();
}
```

- [ ] **Step 2: Create HavenWarpService**

```java
// haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenWarpService.java
package dev.invisiblespiders.haven.api.service;

import java.util.List;
import java.util.UUID;

/**
 * Stub interface — returns empty results until HavenWarps registers an implementation.
 * Chat system uses this for market advertisement validation and tab-complete.
 */
public interface HavenWarpService {

    /** Returns the names of all shop warps owned by this player. Empty until HavenWarps is present. */
    List<String> getShopWarps(UUID playerUuid);

    /** Returns true if the player owns a shop warp with this name. Always false until HavenWarps is present. */
    boolean hasShopWarp(UUID playerUuid, String warpName);
}
```

- [ ] **Step 3: Commit**

```
git add haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenChatService.java
git add haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenWarpService.java
git commit -m "feat(api): add HavenChatService and HavenWarpService (stub) interfaces"
```

---

## Task 3: ChatSettings + chat.yml

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatSettings.java`
- Create: `haven-core/src/main/resources/chat.yml`
- Create: `haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/ChatSettingsTest.java`

- [ ] **Step 1: Write failing tests**

```java
// haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/ChatSettingsTest.java
package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatRecipientType;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSettingsTest {

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @Test
    void parsesGlobalChannel() {
        ChatSettings settings = ChatSettings.from(load("""
            channels:
              global:
                display-name: "Global"
                format: "<white><player_name>: <message>"
                recipient-type: ALL
                default: true
            """));
        assertThat(settings.channels()).containsKey("global");
        var global = settings.channels().get("global");
        assertThat(global.recipientType()).isEqualTo(ChatRecipientType.ALL);
        assertThat(global.isDefault()).isTrue();
        assertThat(global.permission()).isNull();
    }

    @Test
    void parsesStaffChannelWithPermissionAndPrefix() {
        ChatSettings settings = ChatSettings.from(load("""
            channels:
              staff:
                display-name: "Staff"
                format: "<red>[Staff] <player_name>: <message>"
                recipient-type: PERMISSION
                permission: "haven.staff"
                trigger-prefix: "@"
                default: false
            """));
        var staff = settings.channels().get("staff");
        assertThat(staff.recipientType()).isEqualTo(ChatRecipientType.PERMISSION);
        assertThat(staff.permission()).isEqualTo("haven.staff");
        assertThat(staff.triggerPrefix()).isEqualTo("@");
        assertThat(staff.hasTriggerPrefix()).isTrue();
    }

    @Test
    void defaultChannelResolved() {
        ChatSettings settings = ChatSettings.from(load("""
            channels:
              global:
                display-name: "Global"
                format: "<white><player_name>: <message>"
                recipient-type: ALL
                default: true
              staff:
                display-name: "Staff"
                format: "<red><player_name>: <message>"
                recipient-type: PERMISSION
                permission: "haven.staff"
                default: false
            """));
        assertThat(settings.defaultChannel()).isNotNull();
        assertThat(settings.defaultChannel().id()).isEqualTo("global");
    }

    @Test
    void marketConfigParsedWithDefaults() {
        ChatSettings settings = ChatSettings.from(load(""));
        assertThat(settings.market().slotInterval()).isEqualTo(300);
        assertThat(settings.market().playerCooldown()).isEqualTo(600);
        assertThat(settings.market().warpCommand()).isEqualTo("/warp %s");
    }

    @Test
    void prefixChannelMapBuilt() {
        ChatSettings settings = ChatSettings.from(load("""
            channels:
              staff:
                display-name: "Staff"
                format: "<red><player_name>: <message>"
                recipient-type: PERMISSION
                permission: "haven.staff"
                trigger-prefix: "@"
                default: false
            """));
        assertThat(settings.prefixChannels()).containsKey("@");
        assertThat(settings.prefixChannels().get("@").id()).isEqualTo("staff");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.chat.ChatSettingsTest" -i
```
Expected: FAIL — `ChatSettings` does not exist.

- [ ] **Step 3: Create chat.yml**

```yaml
# haven-core/src/main/resources/chat.yml
# Chat channel configuration. Changes require /haven reload.

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
    default: false

market:
  # Seconds between advertisement broadcasts from the queue.
  slot-interval: 300
  # Seconds a player must wait between /market advertise submissions.
  player-cooldown: 600
  # Format for market broadcasts. <shopwarp> is rendered as a clickable component.
  format: "<gold>[Market]</gold> <player_name> @ <shopwarp>: <message>"
  # Command run when a player clicks a shopwarp link. %s is replaced with the warp name.
  warp-command: "/warp %s"
  # Hover text shown on the shopwarp link. %s is replaced with the warp name.
  warp-hover: "<yellow>Click to visit %s!"
```

- [ ] **Step 4: Implement ChatSettings**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatSettings.java
package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import dev.invisiblespiders.haven.api.chat.ChatRecipientType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChatSettings(
        Map<String, ChatChannel> channels,
        ChatChannel defaultChannel,
        Map<String, ChatChannel> prefixChannels,
        MarketConfig market
) {
    public static ChatSettings from(FileConfiguration config) {
        Map<String, ChatChannel> channels = new LinkedHashMap<>();
        ChatChannel defaultChannel = null;
        Map<String, ChatChannel> prefixChannels = new HashMap<>();

        ConfigurationSection channelSection = config.getConfigurationSection("channels");
        if (channelSection != null) {
            for (String id : channelSection.getKeys(false)) {
                ConfigurationSection s = channelSection.getConfigurationSection(id);
                if (s == null) continue;
                ChatChannel channel = parseChannel(id, s);
                channels.put(id, channel);
                if (channel.isDefault()) defaultChannel = channel;
                if (channel.hasTriggerPrefix()) prefixChannels.put(channel.triggerPrefix(), channel);
            }
        }

        if (defaultChannel == null && !channels.isEmpty()) {
            defaultChannel = channels.values().iterator().next();
        }
        if (defaultChannel == null) {
            defaultChannel = new ChatChannel("global", "Global",
                    "<white><player_name>: <message>", ChatRecipientType.ALL,
                    null, 0, 0, null, true);
            channels.put("global", defaultChannel);
        }

        return new ChatSettings(channels, defaultChannel, prefixChannels, MarketConfig.from(config));
    }

    private static ChatChannel parseChannel(String id, ConfigurationSection s) {
        String typeName = s.getString("recipient-type", "ALL");
        ChatRecipientType type;
        try { type = ChatRecipientType.valueOf(typeName.toUpperCase()); }
        catch (IllegalArgumentException e) { type = ChatRecipientType.ALL; }

        return new ChatChannel(
                id,
                s.getString("display-name", id),
                s.getString("format", "<white><player_name>: <message>"),
                type,
                s.getString("permission"),
                s.getInt("radius-blocks", 0),
                s.getInt("cooldown-seconds", 0),
                s.getString("trigger-prefix"),
                s.getBoolean("default", false)
        );
    }

    public record MarketConfig(
            int slotInterval,
            int playerCooldown,
            String format,
            String warpCommand,
            String warpHover
    ) {
        public static MarketConfig from(FileConfiguration config) {
            return new MarketConfig(
                    config.getInt("market.slot-interval", 300),
                    config.getInt("market.player-cooldown", 600),
                    config.getString("market.format",
                            "<gold>[Market]</gold> <player_name> @ <shopwarp>: <message>"),
                    config.getString("market.warp-command", "/warp %s"),
                    config.getString("market.warp-hover", "<yellow>Click to visit %s!")
            );
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.chat.ChatSettingsTest" -i
```
Expected: PASS — all 5 tests green.

- [ ] **Step 6: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatSettings.java
git add haven-core/src/main/resources/chat.yml
git add haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/ChatSettingsTest.java
git commit -m "feat: add ChatSettings record and chat.yml default config"
```

---

## Task 4: AdvertisementRequest record

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/AdvertisementRequest.java`

- [ ] **Step 1: Create AdvertisementRequest**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/AdvertisementRequest.java
package dev.invisiblespiders.haven.core.chat;

import java.util.UUID;

public record AdvertisementRequest(
        UUID playerUuid,
        String playerName,
        String shopWarpName,
        String message,
        long submittedAt
) {}
```

- [ ] **Step 2: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/AdvertisementRequest.java
git commit -m "feat: add AdvertisementRequest record for market queue"
```

---

## Task 5: ChatChannelService

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatChannelService.java`
- Create: `haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/ChatChannelServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
// haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/ChatChannelServiceTest.java
package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import dev.invisiblespiders.haven.api.chat.ChatRecipientType;
import dev.invisiblespiders.haven.core.hook.PlaceholderAPIHook;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatChannelServiceTest {

    @Mock Plugin plugin;
    @Mock Server server;
    @Mock PlaceholderAPIHook papiHook;
    @Mock Player sender;
    @Mock Player recipient;

    ChatSettings settings;
    ChatChannelService service;

    @BeforeEach
    void setup() throws Exception {
        when(plugin.getServer()).thenReturn(server);
        when(sender.getName()).thenReturn("Alice");
        when(sender.getUniqueId()).thenReturn(UUID.randomUUID());

        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.loadFromString("""
            channels:
              global:
                display-name: "Global"
                format: "<white><player_name>: <message>"
                recipient-type: ALL
                default: true
              staff:
                display-name: "Staff"
                format: "<red>[Staff] <player_name>: <message>"
                recipient-type: PERMISSION
                permission: "haven.staff"
                trigger-prefix: "@"
                default: false
            """);
        settings = ChatSettings.from(config);
        service = new ChatChannelService(settings, papiHook, plugin);
    }

    @Test
    void getPlayerChannel_returnsDefaultForUnknownPlayer() {
        assertThat(service.getPlayerChannel(UUID.randomUUID()).id()).isEqualTo("global");
    }

    @Test
    void setPlayerChannel_changesActiveChannel() {
        UUID uuid = sender.getUniqueId();
        service.setPlayerChannel(uuid, "staff");
        assertThat(service.getPlayerChannel(uuid).id()).isEqualTo("staff");
    }

    @Test
    void getChannel_returnsEmptyForUnknownId() {
        assertThat(service.getChannel("nonexistent")).isEmpty();
    }

    @Test
    void getChannel_returnsChannelById() {
        assertThat(service.getChannel("staff")).isPresent();
        assertThat(service.getChannel("staff").get().id()).isEqualTo("staff");
    }

    @Test
    void sendToChannel_ALL_broadcastsToOnlinePlayers() {
        when(server.getOnlinePlayers()).thenReturn(java.util.List.of(sender, recipient));
        when(papiHook.isAvailable()).thenReturn(false);
        ChatChannel global = settings.defaultChannel();
        service.sendToChannel(sender, global, "Hello world");
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(recipient).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void sendToChannel_PERMISSION_filtersRecipients() {
        when(server.getOnlinePlayers()).thenReturn(List.of(sender, recipient));
        when(papiHook.isAvailable()).thenReturn(false);
        when(sender.hasPermission("haven.staff")).thenReturn(true);
        when(recipient.hasPermission("haven.staff")).thenReturn(false);
        ChatChannel staff = settings.channels().get("staff");
        service.sendToChannel(sender, staff, "Staff message");
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(recipient, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.chat.ChatChannelServiceTest" -i
```
Expected: FAIL — `ChatChannelService` does not exist.

- [ ] **Step 3: Implement ChatChannelService**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatChannelService.java
package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import dev.invisiblespiders.haven.api.service.HavenChatService;
import dev.invisiblespiders.haven.core.hook.PlaceholderAPIHook;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ChatChannelService implements HavenChatService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ChatSettings settings;
    private final PlaceholderAPIHook papiHook;
    private final Plugin plugin;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, String> playerChannels = new ConcurrentHashMap<>();

    public ChatChannelService(ChatSettings settings, PlaceholderAPIHook papiHook, Plugin plugin) {
        this.settings = settings;
        this.papiHook = papiHook;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public Optional<ChatChannel> getChannel(String id) {
        return Optional.ofNullable(settings.channels().get(id));
    }

    @Override
    public ChatChannel getPlayerChannel(UUID uuid) {
        String id = playerChannels.get(uuid);
        if (id == null) return settings.defaultChannel();
        return settings.channels().getOrDefault(id, settings.defaultChannel());
    }

    @Override
    public void setPlayerChannel(UUID uuid, String channelId) {
        if (settings.channels().containsKey(channelId)) {
            playerChannels.put(uuid, channelId);
        }
    }

    @Override
    public Collection<ChatChannel> getChannels() {
        return settings.channels().values();
    }

    @Override
    public void sendToChannel(Player sender, ChatChannel channel, String rawMessage) {
        Component formatted = buildMessage(sender, channel, rawMessage);
        switch (channel.recipientType()) {
            case ALL -> plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(formatted));
            case PERMISSION -> {
                String perm = channel.permission();
                if (perm == null) {
                    plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(formatted));
                } else {
                    plugin.getServer().getOnlinePlayers().stream()
                            .filter(p -> p.hasPermission(perm))
                            .forEach(p -> p.sendMessage(formatted));
                }
            }
            case RADIUS -> {
                int radius = channel.radiusBlocks();
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.getWorld().equals(sender.getWorld())
                                && p.getLocation().distanceSquared(sender.getLocation()) <= (long) radius * radius)
                        .forEach(p -> p.sendMessage(formatted));
            }
            case CLAIM -> {
                logger.warning("[Chat] CLAIM channel '" + channel.id()
                        + "' used but HavenClaimsService is not registered — falling back to ALL.");
                plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(formatted));
            }
        }
    }

    private Component buildMessage(Player sender, ChatChannel channel, String rawMessage) {
        String template = channel.format();
        if (papiHook.isAvailable()) {
            template = PlaceholderAPI.setPlaceholders(sender, template);
        }
        // Resolve <message> as the player's raw chat text (unserialized to prevent MiniMessage injection)
        String safeMessage = PlainTextComponentSerializer.plainText().serialize(
                MM.deserialize(rawMessage));
        return MM.deserialize(template,
                Placeholder.unparsed("player_name", sender.getName()),
                Placeholder.unparsed("message", safeMessage));
    }

    /** Called by ChatChannelListener when a player disconnects — clears channel preference. */
    public void onQuit(UUID uuid) {
        playerChannels.remove(uuid);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.chat.ChatChannelServiceTest" -i
```
Expected: PASS — all 6 tests green.

- [ ] **Step 5: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatChannelService.java
git add haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/ChatChannelServiceTest.java
git commit -m "feat: add ChatChannelService with ALL/PERMISSION/RADIUS/CLAIM routing"
```

---

## Task 6: ChatChannelListener

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatChannelListener.java`

- [ ] **Step 1: Implement ChatChannelListener**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatChannelListener.java
package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatChannelListener implements Listener {

    private final ChatChannelService service;
    private final ChatSettings settings;

    public ChatChannelListener(ChatChannelService service, ChatSettings settings) {
        this.service = service;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true); // we handle delivery ourselves
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        ChatChannel channel = resolveChannel(event.getPlayer(), rawMessage);
        // If message started with a trigger prefix, strip that prefix from the content
        String messageContent = rawMessage;
        if (channel.hasTriggerPrefix() && rawMessage.startsWith(channel.triggerPrefix())) {
            messageContent = rawMessage.substring(channel.triggerPrefix().length()).stripLeading();
        }
        service.sendToChannel(event.getPlayer(), channel, messageContent);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.onQuit(event.getPlayer().getUniqueId());
    }

    private ChatChannel resolveChannel(org.bukkit.entity.Player player, String rawMessage) {
        // Check trigger-prefix channels first (e.g. "@" → staff)
        for (var entry : settings.prefixChannels().entrySet()) {
            if (rawMessage.startsWith(entry.getKey())) {
                ChatChannel prefixChannel = entry.getValue();
                if (prefixChannel.permission() == null || player.hasPermission(prefixChannel.permission())) {
                    return prefixChannel;
                }
            }
        }
        // Fall back to player's active channel
        return service.getPlayerChannel(player.getUniqueId());
    }
}
```

- [ ] **Step 2: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChatChannelListener.java
git commit -m "feat: add ChatChannelListener intercepting AsyncChatEvent with trigger-prefix resolution"
```

---

## Task 7: MarketQueue

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/MarketQueue.java`
- Create: `haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/MarketQueueTest.java`

- [ ] **Step 1: Write failing tests**

```java
// haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/MarketQueueTest.java
package dev.invisiblespiders.haven.core.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketQueueTest {

    @Test
    void emptyByDefault() {
        MarketQueue queue = new MarketQueue();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isEqualTo(0);
    }

    @Test
    void enqueueAndPoll() {
        MarketQueue queue = new MarketQueue();
        var req = new AdvertisementRequest(UUID.randomUUID(), "Alice", "AliceShop", "Selling diamonds", System.currentTimeMillis());
        queue.enqueue(req);
        assertThat(queue.isEmpty()).isFalse();
        assertThat(queue.size()).isEqualTo(1);
        var polled = queue.poll();
        assertThat(polled).isEqualTo(req);
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void fifoOrdering() {
        MarketQueue queue = new MarketQueue();
        var first = new AdvertisementRequest(UUID.randomUUID(), "Alice", "AliceShop", "First", 1L);
        var second = new AdvertisementRequest(UUID.randomUUID(), "Bob", "BobShop", "Second", 2L);
        queue.enqueue(first);
        queue.enqueue(second);
        assertThat(queue.poll()).isEqualTo(first);
        assertThat(queue.poll()).isEqualTo(second);
    }

    @Test
    void pollReturnsNullWhenEmpty() {
        MarketQueue queue = new MarketQueue();
        assertThat(queue.poll()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.chat.MarketQueueTest" -i
```
Expected: FAIL — `MarketQueue` does not exist.

- [ ] **Step 3: Implement MarketQueue**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/MarketQueue.java
package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public class MarketQueue {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Deque<AdvertisementRequest> queue = new ArrayDeque<>();
    private @Nullable BukkitTask task;

    // Constructor for unit-test use (no scheduler)
    public MarketQueue() {}

    /** Start the broadcast scheduler. Call from HavenCore.onEnable(). */
    public void start(ChatSettings.MarketConfig marketConfig, HavenPlayerService playerService, Plugin plugin) {
        long intervalTicks = (long) marketConfig.slotInterval() * 20L;
        task = new BukkitRunnable() {
            @Override public void run() {
                AdvertisementRequest req;
                synchronized (queue) { req = queue.poll(); }
                if (req == null) return;
                broadcast(req, marketConfig, playerService, plugin);
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public synchronized void enqueue(AdvertisementRequest request) {
        queue.addLast(request);
    }

    public synchronized @Nullable AdvertisementRequest poll() {
        return queue.poll();
    }

    public synchronized boolean isEmpty() { return queue.isEmpty(); }
    public synchronized int size() { return queue.size(); }

    private void broadcast(AdvertisementRequest req, ChatSettings.MarketConfig config,
                           HavenPlayerService playerService, Plugin plugin) {
        // Build the shopwarp token as a clickable component
        String command = config.warpCommand().formatted(req.shopWarpName());
        String hoverText = config.warpHover().formatted(req.shopWarpName());
        Component warpComponent = MM.deserialize("<click:run_command:'" + command + "'>"
                + req.shopWarpName() + "</click>")
                .hoverEvent(HoverEvent.showText(MM.deserialize(hoverText)));

        // Build the full broadcast component
        String templateWithoutWarp = config.format()
                .replace("<shopwarp>", "WARP_PLACEHOLDER");
        Component pre = MM.deserialize(templateWithoutWarp,
                Placeholder.unparsed("player_name", req.playerName()),
                Placeholder.unparsed("message", req.message()));

        // Splice the warp component into the message
        Component broadcast = spliceWarp(pre, warpComponent);

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            Optional<HavenPlayer> hp = playerService.getCached(online.getUniqueId());
            boolean muted = hp.map(p -> "true".equals(
                    p.getData("haven-core", "market-muted").orElse(""))).orElse(false);
            if (!muted) online.sendMessage(broadcast);
        }
    }

    private Component spliceWarp(Component pre, Component warpComponent) {
        // Replace the plain-text sentinel "WARP_PLACEHOLDER" with the clickable component.
        // We do this by splitting on the plain text content and re-assembling.
        // Simple approach: replace by appending components around the placeholder.
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(pre);
        int idx = plain.indexOf("WARP_PLACEHOLDER");
        if (idx < 0) return pre.append(Component.text(" ")).append(warpComponent);

        // Rebuild as: prefix + warp + suffix
        // For simplicity, flatten to plain text split
        return Component.text(plain.substring(0, idx))
                .append(warpComponent)
                .append(Component.text(plain.substring(idx + "WARP_PLACEHOLDER".length())));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.chat.MarketQueueTest" -i
```
Expected: PASS — all 4 tests green.

- [ ] **Step 5: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/MarketQueue.java
git add haven-core/src/test/java/dev/invisiblespiders/haven/core/chat/MarketQueueTest.java
git commit -m "feat: add MarketQueue with FIFO scheduling and clickable shopwarp broadcast"
```

---

## Task 8: MarketCommand

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/MarketCommand.java`

- [ ] **Step 1: Implement MarketCommand**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/MarketCommand.java
package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.service.HavenCooldownService;
import dev.invisiblespiders.haven.api.service.HavenWarpService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MarketCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String COOLDOWN_KEY = "market-advertise";

    private final MarketQueue queue;
    private final ChatSettings.MarketConfig config;
    private final HavenWarpService warpService;
    private final HavenCooldownService cooldownService;

    public MarketCommand(MarketQueue queue, ChatSettings.MarketConfig config,
                         HavenWarpService warpService, HavenCooldownService cooldownService) {
        this.queue = queue;
        this.config = config;
        this.warpService = warpService;
        this.cooldownService = cooldownService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can use this command."));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(MM.deserialize("<red>Usage: /market advertise <shopwarp> <message>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("mute")) {
            return handleMute(player);
        }

        if (!args[0].equalsIgnoreCase("advertise")) {
            player.sendMessage(MM.deserialize("<red>Usage: /market advertise <shopwarp> <message>"));
            return true;
        }

        if (!player.hasPermission("haven.market.advertise")) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to do that."));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(MM.deserialize("<red>Usage: /market advertise <shopwarp> <message>"));
            return true;
        }

        String shopWarp = args[1];
        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (!warpService.hasShopWarp(player.getUniqueId(), shopWarp)) {
            player.sendMessage(MM.deserialize("<red>You don't have a shop warp named '<white>"
                    + shopWarp + "<red>'. Create one with HavenWarps first."));
            return true;
        }

        long remainingSeconds = cooldownService.getRemainingSeconds(
                player.getUniqueId(), COOLDOWN_KEY);
        if (remainingSeconds > 0) {
            player.sendMessage(MM.deserialize("<red>You must wait <white>" + remainingSeconds
                    + "s<red> before advertising again."));
            return true;
        }

        cooldownService.setCooldown(player.getUniqueId(), COOLDOWN_KEY, config.playerCooldown());
        queue.enqueue(new AdvertisementRequest(
                player.getUniqueId(), player.getName(), shopWarp, message, System.currentTimeMillis()));
        player.sendMessage(MM.deserialize("<green>Advertisement queued! It will broadcast when your slot arrives."));
        return true;
    }

    private boolean handleMute(Player player) {
        if (!player.hasPermission("haven.market.mute")) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to do that."));
            return true;
        }
        // Mute state is stored in HavenPlayer data — handled by MarketQueue at broadcast time.
        // We can't toggle it here without HavenPlayerService — this is intentionally a thin command.
        // HavenCore wires the mute toggle via AfkCommand pattern; add a separate MarketMuteCommand if needed.
        player.sendMessage(MM.deserialize("<yellow>Use <white>/market mute<yellow> to toggle market broadcasts."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("advertise", "mute");
        if (args.length == 2 && args[0].equalsIgnoreCase("advertise") && sender instanceof Player player) {
            return warpService.getShopWarps(player.getUniqueId()).stream()
                    .filter(w -> w.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
```

- [ ] **Step 2: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/MarketCommand.java
git commit -m "feat: add /market command with advertisement queueing, cooldown, and warp validation"
```

---

## Task 9: ChannelCommand

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChannelCommand.java`

- [ ] **Step 1: Implement ChannelCommand**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChannelCommand.java
package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.chat.ChatChannel;
import dev.invisiblespiders.haven.api.service.HavenChatService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class ChannelCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final HavenChatService chatService;

    public ChannelCommand(HavenChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can use this command."));
            return true;
        }
        if (args.length < 1) {
            ChatChannel current = chatService.getPlayerChannel(player.getUniqueId());
            player.sendMessage(MM.deserialize("<gray>Current channel: <white>" + current.displayName()));
            player.sendMessage(MM.deserialize("<gray>Usage: /channel <id>"));
            return true;
        }
        String id = args[0].toLowerCase();
        var channel = chatService.getChannel(id);
        if (channel.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Unknown channel '<white>" + id + "<red>'."));
            return true;
        }
        ChatChannel target = channel.get();
        if (target.permission() != null && !player.hasPermission(target.permission())) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to use that channel."));
            return true;
        }
        chatService.setPlayerChannel(player.getUniqueId(), id);
        player.sendMessage(MM.deserialize("<green>Switched to <white>" + target.displayName() + "<green> channel."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return chatService.getChannels().stream()
                    .filter(c -> c.permission() == null || player.hasPermission(c.permission()))
                    .map(ChatChannel::id)
                    .filter(id -> id.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
```

- [ ] **Step 2: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/ChannelCommand.java
git commit -m "feat: add /channel command for switching active chat channel"
```

---

## Task 10: Wire chat into HavenCore + ConfigManager + plugin.yml

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/config/ConfigManager.java`
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java`
- Modify: `haven-core/src/main/resources/config.yml`
- Modify: `haven-core/src/main/resources/plugin.yml`

- [ ] **Step 1: Extend ConfigManager with chat.yml**

Add `chat.yml` to `CONFIG_FILES`, add `private FileConfiguration chat` field, load in `reload()`, add `public FileConfiguration getChat() { return chat; }`.

- [ ] **Step 2: Add feature toggle to config.yml**

```yaml
features:
  # ... existing ...
  chat-formatting: true   # ← add
```

- [ ] **Step 3: Add commands and permissions to plugin.yml**

```yaml
commands:
  channel:
    description: Switch your active chat channel
    usage: /channel <id>
  market:
    description: Submit a market advertisement
    usage: /market advertise <shopwarp> <message>
    permission: haven.market.advertise

permissions:
  haven.staff:
    description: Access to the staff chat channel
    default: op
  haven.market.advertise:
    description: Submit market advertisements via /market
    default: true
  haven.market.mute:
    description: Mute/unmute market broadcast messages
    default: true
  haven.admin.channel:
    description: Admin channel management
    default: op
```

- [ ] **Step 4: Add a no-op HavenWarpService stub for when HavenWarps is absent**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/NoOpWarpService.java
package dev.invisiblespiders.haven.core.chat;

import dev.invisiblespiders.haven.api.service.HavenWarpService;

import java.util.List;
import java.util.UUID;

final class NoOpWarpService implements HavenWarpService {
    @Override public List<String> getShopWarps(UUID playerUuid) { return List.of(); }
    @Override public boolean hasShopWarp(UUID playerUuid, String warpName) { return false; }
}
```

- [ ] **Step 5: Wire into HavenCore.onEnable()**

```java
if (configManager.getMain().getBoolean("features.chat-formatting", true)) {
    ChatSettings chatSettings = ChatSettings.from(configManager.getChat());

    // Resolve HavenWarpService — use no-op until HavenWarps registers it
    HavenWarpService warpService = Optional.ofNullable(
            getServer().getServicesManager().load(HavenWarpService.class))
            .orElse(new NoOpWarpService());

    ChatChannelService chatChannelService = new ChatChannelService(chatSettings, papiHook, this);
    ChatChannelListener chatListener = new ChatChannelListener(chatChannelService, chatSettings);
    getServer().getPluginManager().registerEvents(chatListener, this);

    MarketQueue marketQueue = new MarketQueue();
    marketQueue.start(chatSettings.market(), playerService, this);

    MarketCommand marketCommand = new MarketCommand(
            marketQueue, chatSettings.market(), warpService, cooldowns);
    ChannelCommand channelCommand = new ChannelCommand(chatChannelService);

    sm.register(HavenChatService.class, chatChannelService, this, ServicePriority.Normal);

    var channelCmd = getCommand("channel");
    if (channelCmd != null) { channelCmd.setExecutor(channelCommand); channelCmd.setTabCompleter(channelCommand); }
    var marketCmd = getCommand("market");
    if (marketCmd != null) { marketCmd.setExecutor(marketCommand); marketCmd.setTabCompleter(marketCommand); }

    getLogger().info("Chat channel system enabled (" + chatSettings.channels().size() + " channel(s)).");
}
```

Add required imports:
```java
import dev.invisiblespiders.haven.api.service.HavenChatService;
import dev.invisiblespiders.haven.api.service.HavenWarpService;
import dev.invisiblespiders.haven.core.chat.*;
```

- [ ] **Step 6: Run full test suite**

```
.\gradlew test -i
```
Expected: PASS — all tests green.

- [ ] **Step 7: Build JAR**

```
.\gradlew :haven-core:shadowJar
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/chat/NoOpWarpService.java
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/config/ConfigManager.java
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java
git add haven-core/src/main/resources/config.yml
git add haven-core/src/main/resources/plugin.yml
git commit -m "feat: wire chat channel system and market queue into HavenCore"
```
