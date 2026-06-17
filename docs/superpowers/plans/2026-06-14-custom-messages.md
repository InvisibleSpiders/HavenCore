# Custom Messages System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow players to unlock and select custom join/quit/AFK message presets, set personal messages via commands, and receive randomised per-cause death messages (with a premium pool gated by permission).

**Architecture:** `PlayerMessageService` resolves message priority: personal custom → selected preset → server default. `PlayerMessageListener` intercepts `PlayerJoinEvent`, `PlayerQuitEvent`, and `PlayerDeathEvent`, nulls Paper's default messages, and broadcasts custom ones. `MiniMessageSanitizer` blocks unsafe MiniMessage tags and configurable regex patterns. Preset unlocks are stored in `HavenPlayer.getData()` as comma-separated IDs. `AfkManager` calls `PlayerMessageService.getAfkMessage()` for AFK broadcasts when this module is enabled (wired via `AfkManager.setMessageService()`).

**Tech Stack:** Paper 26.1 API, Adventure MiniMessage, JUnit Jupiter 5.11, Mockito 5.23.

**Prerequisite:** Plan `2026-06-14-afk-tab.md` must be complete — specifically `AfkManager.setMessageService()`.

---

## File Map

**Create (haven-api):**
- `haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenMessageService.java`

**Create (haven-core):**
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/UnlockType.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PresetDefinition.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/DeathMessageEntry.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/FilterSettings.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/MessageSettings.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/MiniMessageSanitizer.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PlayerMessageService.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PlayerMessageListener.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/JoinMsgCommand.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/QuitMsgCommand.java`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/AfkMsgCommand.java`
- `haven-core/src/main/resources/messages.yml`

**Create (tests):**
- `haven-core/src/test/java/dev/invisiblespiders/haven/core/message/MessageSettingsTest.java`
- `haven-core/src/test/java/dev/invisiblespiders/haven/core/message/MiniMessageSanitizerTest.java`
- `haven-core/src/test/java/dev/invisiblespiders/haven/core/message/PlayerMessageServiceTest.java`

**Modify:**
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/config/ConfigManager.java` — add `messages.yml`
- `haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java` — wire module
- `haven-core/src/main/resources/config.yml` — add `features.custom-messages`
- `haven-core/src/main/resources/plugin.yml` — add `/joinmsg`, `/quitmsg`, `/afkmsg` + permissions

---

## Task 1: HavenMessageService API

**Files:**
- Create: `haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenMessageService.java`

- [ ] **Step 1: Create HavenMessageService**

```java
// haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenMessageService.java
package dev.invisiblespiders.haven.api.service;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface HavenMessageService {

    /** Returns the formatted join broadcast for this player. */
    Component getJoinMessage(Player player);

    /** Returns the formatted quit broadcast for this player. */
    Component getQuitMessage(Player player);

    /** Returns the formatted AFK broadcast for this player. */
    Component getAfkMessage(Player player);

    /**
     * Returns a random death message for this player given the damage cause and optional killer.
     * Selects from messages the player's permission allows.
     */
    Component getDeathMessage(Player player, EntityDamageEvent.DamageCause cause,
                               @Nullable Entity killer);

    /** Grants the named preset ID to this player (stored in HavenPlayer data). */
    void unlockPreset(UUID uuid, String presetId);

    /** Returns all preset IDs currently unlocked for this player. */
    List<String> getUnlockedPresets(UUID uuid);
}
```

- [ ] **Step 2: Commit**

```
git add haven-api/src/main/java/dev/invisiblespiders/haven/api/service/HavenMessageService.java
git commit -m "feat(api): add HavenMessageService interface"
```

---

## Task 2: Model records — UnlockType, PresetDefinition, DeathMessageEntry, FilterSettings

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/UnlockType.java`
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PresetDefinition.java`
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/DeathMessageEntry.java`
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/FilterSettings.java`

- [ ] **Step 1: Create UnlockType**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/UnlockType.java
package dev.invisiblespiders.haven.core.message;

public enum UnlockType {
    /** Available to all players immediately. */
    FREE,
    /** Granted automatically when a matching HavenCodexMilestoneEvent fires. */
    CODEX,
    /** Player must hold the configured permission node. */
    PERMISSION,
    /** Only unlocked via admin command /haven messages unlock. */
    ADMIN
}
```

- [ ] **Step 2: Create PresetDefinition**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PresetDefinition.java
package dev.invisiblespiders.haven.core.message;

import org.jetbrains.annotations.Nullable;

public record PresetDefinition(
        String id,
        String message,
        UnlockType unlockType,
        @Nullable String codexMilestone,
        @Nullable String permission,
        boolean isDefault
) {
    /** Returns true if this preset is accessible to the given player (by permission check). */
    public boolean isAvailableByPermission(org.bukkit.entity.Player player) {
        return switch (unlockType) {
            case FREE -> true;
            case PERMISSION -> permission != null && player.hasPermission(permission);
            case CODEX, ADMIN -> false; // these require explicit unlock grant
        };
    }
}
```

- [ ] **Step 3: Create DeathMessageEntry**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/DeathMessageEntry.java
package dev.invisiblespiders.haven.core.message;

import org.jetbrains.annotations.Nullable;

public record DeathMessageEntry(
        String message,
        @Nullable String permission
) {
    /** True if this message has no permission gate (available to all). */
    public boolean isUnrestricted() { return permission == null || permission.isBlank(); }
}
```

- [ ] **Step 4: Create FilterSettings**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/FilterSettings.java
package dev.invisiblespiders.haven.core.message;

import java.util.List;

public record FilterSettings(int maxLength, List<String> blockedPatterns) {
    public static FilterSettings defaults() {
        return new FilterSettings(100, List.of());
    }
}
```

- [ ] **Step 5: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/UnlockType.java
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PresetDefinition.java
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/DeathMessageEntry.java
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/FilterSettings.java
git commit -m "feat: add UnlockType, PresetDefinition, DeathMessageEntry, FilterSettings records"
```

---

## Task 3: MessageSettings + messages.yml

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/MessageSettings.java`
- Create: `haven-core/src/main/resources/messages.yml`
- Create: `haven-core/src/test/java/dev/invisiblespiders/haven/core/message/MessageSettingsTest.java`

- [ ] **Step 1: Write failing tests**

```java
// haven-core/src/test/java/dev/invisiblespiders/haven/core/message/MessageSettingsTest.java
package dev.invisiblespiders.haven.core.message;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSettingsTest {

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @Test
    void defaultPresetPresentInJoinPresets() {
        MessageSettings settings = MessageSettings.from(load(""));
        assertThat(settings.joinPresets()).containsKey("default");
        assertThat(settings.joinPresets().get("default").isDefault()).isTrue();
    }

    @Test
    void defaultPresetPresentInQuitPresets() {
        MessageSettings settings = MessageSettings.from(load(""));
        assertThat(settings.quitPresets()).containsKey("default");
    }

    @Test
    void parsesJoinPresetWithUnlockType() {
        MessageSettings settings = MessageSettings.from(load("""
            join-presets:
              default:
                message: "<green>→ <player> joined."
                unlock-type: FREE
              vip-entry:
                message: "<gold><player> has arrived."
                unlock-type: PERMISSION
                permission: "haven.messages.preset.vip-entry"
            """));
        assertThat(settings.joinPresets().get("vip-entry").unlockType()).isEqualTo(UnlockType.PERMISSION);
        assertThat(settings.joinPresets().get("vip-entry").permission()).isEqualTo("haven.messages.preset.vip-entry");
    }

    @Test
    void parsesDeathMessages() {
        MessageSettings settings = MessageSettings.from(load("""
            death-messages:
              FALL:
                - message: "<player> hit the ground too hard."
                - message: "<player> forgot how to land."
                  permission: "haven.messages.premium"
            """));
        var fallMessages = settings.deathMessages().get(EntityDamageEvent.DamageCause.FALL);
        assertThat(fallMessages).hasSize(2);
        assertThat(fallMessages.get(0).isUnrestricted()).isTrue();
        assertThat(fallMessages.get(1).permission()).isEqualTo("haven.messages.premium");
    }

    @Test
    void unknownCausePoolPresentByDefault() {
        MessageSettings settings = MessageSettings.from(load(""));
        assertThat(settings.deathMessages()).containsKey(EntityDamageEvent.DamageCause.UNKNOWN);
    }

    @Test
    void filterSettingsParsed() {
        MessageSettings settings = MessageSettings.from(load("""
            filter:
              max-length: 50
              blocked-patterns:
                - "(?i)badword"
            """));
        assertThat(settings.filter().maxLength()).isEqualTo(50);
        assertThat(settings.filter().blockedPatterns()).containsExactly("(?i)badword");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.message.MessageSettingsTest" -i
```
Expected: FAIL — `MessageSettings` does not exist.

- [ ] **Step 3: Create messages.yml**

```yaml
# haven-core/src/main/resources/messages.yml
# Custom message system configuration. Changes require /haven reload.

filter:
  max-length: 100
  blocked-patterns:
    - "(?i)(example_blocked_word)"
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

- [ ] **Step 4: Implement MessageSettings**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/MessageSettings.java
package dev.invisiblespiders.haven.core.message;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MessageSettings(
        FilterSettings filter,
        Map<String, PresetDefinition> joinPresets,
        Map<String, PresetDefinition> quitPresets,
        Map<String, PresetDefinition> afkPresets,
        Map<EntityDamageEvent.DamageCause, List<DeathMessageEntry>> deathMessages
) {
    public static MessageSettings from(FileConfiguration config) {
        return new MessageSettings(
                parseFilter(config),
                parsePresets(config, "join-presets", "<green>→ <player> joined."),
                parsePresets(config, "quit-presets", "<gray>← <player> left."),
                parsePresets(config, "afk-presets", "<gray><player> is now AFK."),
                parseDeathMessages(config)
        );
    }

    public PresetDefinition defaultJoinPreset() {
        return joinPresets.values().stream().filter(PresetDefinition::isDefault)
                .findFirst().orElse(joinPresets.values().iterator().next());
    }

    public PresetDefinition defaultQuitPreset() {
        return quitPresets.values().stream().filter(PresetDefinition::isDefault)
                .findFirst().orElse(quitPresets.values().iterator().next());
    }

    public PresetDefinition defaultAfkPreset() {
        return afkPresets.values().stream().filter(PresetDefinition::isDefault)
                .findFirst().orElse(afkPresets.values().iterator().next());
    }

    private static FilterSettings parseFilter(FileConfiguration config) {
        int maxLength = config.getInt("filter.max-length", 100);
        List<String> patterns = config.getStringList("filter.blocked-patterns");
        return new FilterSettings(maxLength, patterns);
    }

    private static Map<String, PresetDefinition> parsePresets(FileConfiguration config,
                                                               String section, String defaultMessage) {
        Map<String, PresetDefinition> presets = new LinkedHashMap<>();
        ConfigurationSection cs = config.getConfigurationSection(section);
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                String typeName = s.getString("unlock-type", "FREE");
                UnlockType unlockType;
                try { unlockType = UnlockType.valueOf(typeName.toUpperCase()); }
                catch (IllegalArgumentException e) { unlockType = UnlockType.FREE; }
                presets.put(id, new PresetDefinition(
                        id,
                        s.getString("message", defaultMessage),
                        unlockType,
                        s.getString("codex-milestone"),
                        s.getString("permission"),
                        s.getBoolean("default", false)
                ));
            }
        }
        if (presets.isEmpty()) {
            presets.put("default", new PresetDefinition("default", defaultMessage,
                    UnlockType.FREE, null, null, true));
        }
        return presets;
    }

    private static Map<EntityDamageEvent.DamageCause, List<DeathMessageEntry>> parseDeathMessages(
            FileConfiguration config) {
        Map<EntityDamageEvent.DamageCause, List<DeathMessageEntry>> map = new EnumMap<>(EntityDamageEvent.DamageCause.class);
        ConfigurationSection section = config.getConfigurationSection("death-messages");
        if (section != null) {
            for (String causeName : section.getKeys(false)) {
                EntityDamageEvent.DamageCause cause;
                try { cause = EntityDamageEvent.DamageCause.valueOf(causeName.toUpperCase()); }
                catch (IllegalArgumentException e) { continue; }
                List<Map<?, ?>> entries = config.getMapList("death-messages." + causeName);
                List<DeathMessageEntry> messages = new ArrayList<>();
                for (Map<?, ?> entry : entries) {
                    String msg = String.valueOf(entry.getOrDefault("message", causeName + " death."));
                    String perm = entry.containsKey("permission") ? String.valueOf(entry.get("permission")) : null;
                    messages.add(new DeathMessageEntry(msg, perm));
                }
                if (!messages.isEmpty()) map.put(cause, messages);
            }
        }
        // Ensure UNKNOWN fallback exists
        map.computeIfAbsent(EntityDamageEvent.DamageCause.UNKNOWN,
                k -> List.of(new DeathMessageEntry("<player> died.", null)));
        return map;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.message.MessageSettingsTest" -i
```
Expected: PASS — all 6 tests green.

- [ ] **Step 6: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/MessageSettings.java
git add haven-core/src/main/resources/messages.yml
git add haven-core/src/test/java/dev/invisiblespiders/haven/core/message/MessageSettingsTest.java
git commit -m "feat: add MessageSettings record and messages.yml with all death cause entries"
```

---

## Task 4: MiniMessageSanitizer

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/MiniMessageSanitizer.java`
- Create: `haven-core/src/test/java/dev/invisiblespiders/haven/core/message/MiniMessageSanitizerTest.java`

- [ ] **Step 1: Write failing tests**

```java
// haven-core/src/test/java/dev/invisiblespiders/haven/core/message/MiniMessageSanitizerTest.java
package dev.invisiblespiders.haven.core.message;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniMessageSanitizerTest {

    private final MiniMessageSanitizer sanitizer = new MiniMessageSanitizer(
            new FilterSettings(50, List.of("(?i)badword", "(?i)https?://")));

    @Test
    void allowsPlainText() {
        assertThat(sanitizer.sanitize("Hello world")).isEqualTo("Hello world");
    }

    @Test
    void allowsColorTags() {
        assertThat(sanitizer.sanitize("<red>Hello</red>")).isEqualTo("<red>Hello</red>");
    }

    @Test
    void allowsGradientTag() {
        assertThat(sanitizer.sanitize("<gradient:red:blue>text</gradient>"))
                .isEqualTo("<gradient:red:blue>text</gradient>");
    }

    @Test
    void stripsClickTag() {
        String result = sanitizer.sanitize("<click:run_command:/op badguy>text</click>");
        assertThat(result).doesNotContain("<click");
        assertThat(result).contains("text");
    }

    @Test
    void stripsHoverTag() {
        String result = sanitizer.sanitize("<hover:show_text:'evil'>text</hover>");
        assertThat(result).doesNotContain("<hover");
    }

    @Test
    void stripsInsertionTag() {
        String result = sanitizer.sanitize("<insertion:secret>text</insertion>");
        assertThat(result).doesNotContain("<insertion");
    }

    @Test
    void throwsOnBlockedPattern() {
        assertThatThrownBy(() -> sanitizer.sanitize("buy at https://spamsite.com"))
                .isInstanceOf(MiniMessageSanitizer.BlockedContentException.class);
    }

    @Test
    void throwsOnBlockedWord() {
        assertThatThrownBy(() -> sanitizer.sanitize("you are a badword"))
                .isInstanceOf(MiniMessageSanitizer.BlockedContentException.class);
    }

    @Test
    void throwsWhenExceedsMaxLength() {
        String longInput = "a".repeat(51);
        assertThatThrownBy(() -> sanitizer.sanitize(longInput))
                .isInstanceOf(MiniMessageSanitizer.BlockedContentException.class)
                .hasMessageContaining("length");
    }

    @Test
    void allowsMaxLengthExactly() {
        String input = "a".repeat(50);
        assertThat(sanitizer.sanitize(input)).isEqualTo(input);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.message.MiniMessageSanitizerTest" -i
```
Expected: FAIL — `MiniMessageSanitizer` does not exist.

- [ ] **Step 3: Implement MiniMessageSanitizer**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/MiniMessageSanitizer.java
package dev.invisiblespiders.haven.core.message;

import java.util.List;
import java.util.regex.Pattern;

public class MiniMessageSanitizer {

    // Tags that may be present in output — everything else is stripped.
    private static final List<String> ALLOWED_OPEN_TAGS = List.of(
            "color", "colour", "gradient", "rainbow", "bold", "b", "italic", "i",
            "underlined", "u", "strikethrough", "st", "obfuscated", "obf", "reset",
            "newline", "br",
            // Named colors
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white"
    );

    // These open-tag prefixes are always blocked, even if they appear inside other text.
    private static final List<String> BLOCKED_TAG_PREFIXES = List.of(
            "click", "hover", "insertion", "font", "transition", "selector", "nbt",
            "score", "translate", "lang", "key"
    );

    private static final Pattern TAG_PATTERN = Pattern.compile("<([^/>][^>]*)>");

    private final FilterSettings filter;
    private final List<Pattern> compiledPatterns;

    public MiniMessageSanitizer(FilterSettings filter) {
        this.filter = filter;
        this.compiledPatterns = filter.blockedPatterns().stream()
                .map(Pattern::compile)
                .toList();
    }

    /**
     * Sanitizes a raw MiniMessage string: strips blocked tags, checks length, checks regex patterns.
     *
     * @throws BlockedContentException if the input violates any filter rule.
     */
    public String sanitize(String input) {
        if (input.length() > filter.maxLength()) {
            throw new BlockedContentException("Message exceeds maximum length of " + filter.maxLength() + " characters.");
        }
        String stripped = stripBlockedTags(input);
        // Run regex checks against plain text (after stripping tags)
        String plainText = stripped.replaceAll("<[^>]*>", "");
        for (Pattern pattern : compiledPatterns) {
            if (pattern.matcher(plainText).find()) {
                throw new BlockedContentException("Message contains blocked content.");
            }
        }
        return stripped;
    }

    private String stripBlockedTags(String input) {
        var sb = new StringBuilder();
        var matcher = TAG_PATTERN.matcher(input);
        int lastEnd = 0;
        while (matcher.find()) {
            String tagContent = matcher.group(1).toLowerCase().trim();
            String tagName = tagContent.startsWith("/") ? tagContent.substring(1) : tagContent;
            // Get just the tag name without parameters (e.g. "click:run_command:..." → "click")
            int colon = tagName.indexOf(':');
            String baseTagName = colon >= 0 ? tagName.substring(0, colon) : tagName;
            int space = baseTagName.indexOf(' ');
            if (space >= 0) baseTagName = baseTagName.substring(0, space);

            boolean blocked = BLOCKED_TAG_PREFIXES.stream()
                    .anyMatch(blocked -> baseTagName.startsWith(blocked));
            boolean allowed = !blocked && (
                    ALLOWED_OPEN_TAGS.contains(baseTagName)
                    || baseTagName.startsWith("#")  // hex colors
                    || baseTagName.isEmpty()         // closing tags like </>
            );

            if (allowed) {
                sb.append(input, lastEnd, matcher.end());
            } else {
                // Append text before this tag, skip the tag
                sb.append(input, lastEnd, matcher.start());
            }
            lastEnd = matcher.end();
        }
        sb.append(input, lastEnd, input.length());
        return sb.toString();
    }

    public static class BlockedContentException extends RuntimeException {
        public BlockedContentException(String message) { super(message); }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.message.MiniMessageSanitizerTest" -i
```
Expected: PASS — all 10 tests green.

- [ ] **Step 5: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/MiniMessageSanitizer.java
git add haven-core/src/test/java/dev/invisiblespiders/haven/core/message/MiniMessageSanitizerTest.java
git commit -m "feat: add MiniMessageSanitizer with tag allowlist and regex blocklist"
```

---

## Task 5: PlayerMessageService — message resolution

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PlayerMessageService.java`
- Create: `haven-core/src/test/java/dev/invisiblespiders/haven/core/message/PlayerMessageServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
// haven-core/src/test/java/dev/invisiblespiders/haven/core/message/PlayerMessageServiceTest.java
package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerMessageServiceTest {

    @Mock HavenPlayerService playerService;
    @Mock Player player;
    @Mock HavenPlayer havenPlayer;

    UUID uuid;
    MessageSettings settings;
    PlayerMessageService service;

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(yaml); }
        catch (InvalidConfigurationException e) { throw new RuntimeException(e); }
        return config;
    }

    @BeforeEach
    void setup() {
        uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("TestPlayer");
        when(playerService.getCached(uuid)).thenReturn(Optional.of(havenPlayer));

        settings = MessageSettings.from(load("""
            join-presets:
              default:
                message: "<green>→ <player> joined."
                unlock-type: FREE
              vip-entry:
                message: "<gold><player> the VIP arrived."
                unlock-type: PERMISSION
                permission: "haven.messages.preset.vip-entry"
            quit-presets:
              default:
                message: "<gray>← <player> left."
                unlock-type: FREE
            afk-presets:
              default:
                message: "<gray><player> is AFK."
                unlock-type: FREE
            """));
        service = new PlayerMessageService(settings, playerService,
                new MiniMessageSanitizer(FilterSettings.defaults()));
    }

    @Test
    void joinMessage_usesDefaultWhenNoSelection() {
        when(havenPlayer.getData("haven-core", "join-msg-type")).thenReturn(Optional.empty());
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(service.getJoinMessage(player));
        assertThat(plain).contains("TestPlayer").contains("joined");
    }

    @Test
    void joinMessage_usesCustomWhenSet() {
        when(havenPlayer.getData("haven-core", "join-msg-type")).thenReturn(Optional.of("custom"));
        when(havenPlayer.getData("haven-core", "join-msg-custom"))
                .thenReturn(Optional.of("TestPlayer arrives!"));
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(service.getJoinMessage(player));
        assertThat(plain).isEqualTo("TestPlayer arrives!");
    }

    @Test
    void joinMessage_usesPresetWhenSelected() {
        when(havenPlayer.getData("haven-core", "join-msg-type")).thenReturn(Optional.of("preset"));
        when(havenPlayer.getData("haven-core", "join-msg-preset")).thenReturn(Optional.of("vip-entry"));
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(service.getJoinMessage(player));
        assertThat(plain).contains("VIP").contains("TestPlayer");
    }

    @Test
    void unlockPreset_addsToUnlockedList() {
        when(havenPlayer.getData("haven-core", "unlocked-presets")).thenReturn(Optional.empty());
        service.unlockPreset(uuid, "chicken-slayer");
        verify(havenPlayer).setData("haven-core", "unlocked-presets", "chicken-slayer");
    }

    @Test
    void unlockPreset_appendsToExistingList() {
        when(havenPlayer.getData("haven-core", "unlocked-presets"))
                .thenReturn(Optional.of("wave"));
        service.unlockPreset(uuid, "chicken-slayer");
        verify(havenPlayer).setData("haven-core", "unlocked-presets", "wave,chicken-slayer");
    }

    @Test
    void getUnlockedPresets_returnsEmptyWhenNone() {
        when(havenPlayer.getData("haven-core", "unlocked-presets")).thenReturn(Optional.empty());
        assertThat(service.getUnlockedPresets(uuid)).isEmpty();
    }

    @Test
    void getUnlockedPresets_parsesCommaSeparated() {
        when(havenPlayer.getData("haven-core", "unlocked-presets"))
                .thenReturn(Optional.of("wave,chicken-slayer"));
        assertThat(service.getUnlockedPresets(uuid)).containsExactly("wave", "chicken-slayer");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.message.PlayerMessageServiceTest" -i
```
Expected: FAIL — `PlayerMessageService` does not exist.

- [ ] **Step 3: Implement PlayerMessageService**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PlayerMessageService.java
package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.event.HavenCodexMilestoneEvent;
import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PlayerMessageService implements HavenMessageService, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final MessageSettings settings;
    private final HavenPlayerService playerService;
    private final MiniMessageSanitizer sanitizer;

    public PlayerMessageService(MessageSettings settings, HavenPlayerService playerService,
                                 MiniMessageSanitizer sanitizer) {
        this.settings = settings;
        this.playerService = playerService;
        this.sanitizer = sanitizer;
    }

    @Override
    public Component getJoinMessage(Player player) {
        return resolve(player, "join", settings.joinPresets(),
                settings.defaultJoinPreset().message());
    }

    @Override
    public Component getQuitMessage(Player player) {
        return resolve(player, "quit", settings.quitPresets(),
                settings.defaultQuitPreset().message());
    }

    @Override
    public Component getAfkMessage(Player player) {
        return resolve(player, "afk", settings.afkPresets(),
                settings.defaultAfkPreset().message());
    }

    @Override
    public Component getDeathMessage(Player player, EntityDamageEvent.DamageCause cause,
                                      Entity killer) {
        var pool = settings.deathMessages().getOrDefault(cause,
                settings.deathMessages().getOrDefault(
                        EntityDamageEvent.DamageCause.UNKNOWN, List.of()));
        List<DeathMessageEntry> eligible = pool.stream()
                .filter(e -> e.isUnrestricted() || player.hasPermission(e.permission()))
                .collect(Collectors.toList());
        if (eligible.isEmpty()) {
            eligible = List.of(new DeathMessageEntry("<player> died.", null));
        }
        DeathMessageEntry chosen = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        String killerName = killer != null ? killer.getName() : "unknown";
        return MM.deserialize(chosen.message(),
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("killer", killerName));
    }

    @Override
    public void unlockPreset(UUID uuid, String presetId) {
        playerService.getCached(uuid).ifPresent(hp -> {
            List<String> existing = getUnlockedPresetsFromPlayer(hp);
            if (!existing.contains(presetId)) {
                String updated = existing.isEmpty() ? presetId
                        : String.join(",", existing) + "," + presetId;
                hp.setData("haven-core", "unlocked-presets", updated);
                playerService.save(hp);
            }
        });
    }

    @Override
    public List<String> getUnlockedPresets(UUID uuid) {
        return playerService.getCached(uuid)
                .map(this::getUnlockedPresetsFromPlayer)
                .orElse(List.of());
    }

    @EventHandler
    public void onCodexMilestone(HavenCodexMilestoneEvent event) {
        String milestoneId = event.getMilestoneId();
        for (var presets : List.of(settings.joinPresets(), settings.quitPresets(), settings.afkPresets())) {
            presets.values().stream()
                    .filter(p -> p.unlockType() == UnlockType.CODEX
                            && milestoneId.equals(p.codexMilestone()))
                    .forEach(p -> unlockPreset(event.getPlayer().getUniqueId(), p.id()));
        }
    }

    private Component resolve(Player player, String type,
                               java.util.Map<String, PresetDefinition> presets, String fallback) {
        Optional<HavenPlayer> hp = playerService.getCached(player.getUniqueId());
        if (hp.isEmpty()) return deserializeTemplate(fallback, player);

        HavenPlayer havenPlayer = hp.get();
        String msgType = havenPlayer.getData("haven-core", type + "-msg-type").orElse(null);

        if ("custom".equals(msgType)) {
            String custom = havenPlayer.getData("haven-core", type + "-msg-custom").orElse(null);
            if (custom != null && !custom.isBlank()) return MM.deserialize(custom);
        }

        if ("preset".equals(msgType)) {
            String presetId = havenPlayer.getData("haven-core", type + "-msg-preset").orElse(null);
            PresetDefinition preset = presets.get(presetId);
            if (preset != null) return deserializeTemplate(preset.message(), player);
        }

        return deserializeTemplate(fallback, player);
    }

    private Component deserializeTemplate(String template, Player player) {
        return MM.deserialize(template, Placeholder.unparsed("player", player.getName()));
    }

    private List<String> getUnlockedPresetsFromPlayer(HavenPlayer hp) {
        String raw = hp.getData("haven-core", "unlocked-presets").orElse("");
        if (raw.isBlank()) return List.of();
        return Arrays.asList(raw.split(","));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew :haven-core:test --tests "dev.invisiblespiders.haven.core.message.PlayerMessageServiceTest" -i
```
Expected: PASS — all 7 tests green.

- [ ] **Step 5: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PlayerMessageService.java
git add haven-core/src/test/java/dev/invisiblespiders/haven/core/message/PlayerMessageServiceTest.java
git commit -m "feat: add PlayerMessageService with priority chain resolution and preset unlock management"
```

---

## Task 6: PlayerMessageListener

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PlayerMessageListener.java`

- [ ] **Step 1: Implement PlayerMessageListener**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PlayerMessageListener.java
package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.service.HavenMessageService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerMessageListener implements Listener {

    private final HavenMessageService messageService;

    public PlayerMessageListener(HavenMessageService messageService) {
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null); // suppress Paper's default
        Player player = event.getPlayer();
        // Broadcast to all online players (including the joining player)
        player.getServer().getOnlinePlayers().forEach(p ->
                p.sendMessage(messageService.getJoinMessage(player)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null); // suppress Paper's default
        Player player = event.getPlayer();
        player.getServer().getOnlinePlayers().stream()
                .filter(p -> !p.equals(player)) // player has left — don't send to them
                .forEach(p -> p.sendMessage(messageService.getQuitMessage(player)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null); // suppress Paper's default
        Player player = event.getEntity();
        var lastDamage = player.getLastDamageCause();
        var cause = lastDamage != null
                ? lastDamage.getCause()
                : org.bukkit.event.entity.EntityDamageEvent.DamageCause.UNKNOWN;
        Entity killer = player.getKiller();
        player.getServer().getOnlinePlayers().forEach(p ->
                p.sendMessage(messageService.getDeathMessage(player, cause, killer)));
    }
}
```

- [ ] **Step 2: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/PlayerMessageListener.java
git commit -m "feat: add PlayerMessageListener for join/quit/death broadcast overrides"
```

---

## Task 7: JoinMsgCommand, QuitMsgCommand, AfkMsgCommand

**Files:**
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/JoinMsgCommand.java`
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/QuitMsgCommand.java`
- Create: `haven-core/src/main/java/dev/invisiblespiders/haven/core/message/AfkMsgCommand.java`

- [ ] **Step 1: Create a shared base — AbstractMsgCommand**

All three commands share identical logic with different `type` strings. Extract to an abstract base:

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/AbstractMsgCommand.java
package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.model.HavenPlayer;
import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

abstract class AbstractMsgCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final String type;   // "join", "quit", or "afk"
    private final HavenPlayerService playerService;
    private final HavenMessageService messageService;
    private final MiniMessageSanitizer sanitizer;
    private final Map<String, PresetDefinition> presets;

    AbstractMsgCommand(String type, Map<String, PresetDefinition> presets,
                        HavenPlayerService playerService, HavenMessageService messageService,
                        MiniMessageSanitizer sanitizer) {
        this.type = type;
        this.presets = presets;
        this.playerService = playerService;
        this.messageService = messageService;
        this.sanitizer = sanitizer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can use this command."));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(MM.deserialize("<red>Usage: /" + label + " set|select|clear [value]"));
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "set" -> handleSet(player, args);
            case "select" -> handleSelect(player, args);
            case "clear" -> handleClear(player);
            default -> {
                player.sendMessage(MM.deserialize("<red>Usage: /" + label + " set|select|clear [value]"));
                yield true;
            }
        };
    }

    private boolean handleSet(Player player, String[] args) {
        if (!player.hasPermission("haven.messages.custom")) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to set a custom message."));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MM.deserialize("<red>Usage: /" + type + "msg set <message>"));
            return true;
        }
        String raw = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String sanitized;
        try {
            boolean bypass = player.hasPermission("haven.messages.bypass-filter");
            sanitized = bypass ? raw : sanitizer.sanitize(raw);
        } catch (MiniMessageSanitizer.BlockedContentException e) {
            player.sendMessage(MM.deserialize("<red>" + e.getMessage()));
            return true;
        }
        Optional<HavenPlayer> hp = playerService.getCached(player.getUniqueId());
        if (hp.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Profile not loaded yet. Try again."));
            return true;
        }
        hp.get().setData("haven-core", type + "-msg-type", "custom");
        hp.get().setData("haven-core", type + "-msg-custom", sanitized);
        playerService.save(hp.get());
        player.sendMessage(MM.deserialize("<green>Custom " + type + " message set."));
        return true;
    }

    private boolean handleSelect(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MM.deserialize("<red>Usage: /" + type + "msg select <preset-id>"));
            return true;
        }
        String presetId = args[1];
        PresetDefinition preset = presets.get(presetId);
        if (preset == null) {
            player.sendMessage(MM.deserialize("<red>Unknown preset '<white>" + presetId + "<red>'."));
            return true;
        }
        // Check unlock eligibility
        boolean unlocked = preset.unlockType() == UnlockType.FREE
                || preset.isAvailableByPermission(player)
                || messageService.getUnlockedPresets(player.getUniqueId()).contains(presetId);
        if (!unlocked) {
            player.sendMessage(MM.deserialize("<red>You haven't unlocked that preset yet."));
            return true;
        }
        Optional<HavenPlayer> hp = playerService.getCached(player.getUniqueId());
        if (hp.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Profile not loaded yet. Try again."));
            return true;
        }
        hp.get().setData("haven-core", type + "-msg-type", "preset");
        hp.get().setData("haven-core", type + "-msg-preset", presetId);
        playerService.save(hp.get());
        player.sendMessage(MM.deserialize("<green>Selected '<white>" + presetId + "<green>' as your " + type + " message."));
        return true;
    }

    private boolean handleClear(Player player) {
        Optional<HavenPlayer> hp = playerService.getCached(player.getUniqueId());
        if (hp.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Profile not loaded yet. Try again."));
            return true;
        }
        hp.get().removeData("haven-core", type + "-msg-type");
        hp.get().removeData("haven-core", type + "-msg-custom");
        hp.get().removeData("haven-core", type + "-msg-preset");
        playerService.save(hp.get());
        player.sendMessage(MM.deserialize("<green>Cleared your custom " + type + " message."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("set", "select", "clear");
        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            return presets.keySet().stream()
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
```

- [ ] **Step 2: Create the three concrete commands**

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/JoinMsgCommand.java
package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;

public class JoinMsgCommand extends AbstractMsgCommand {
    public JoinMsgCommand(MessageSettings settings, HavenPlayerService playerService,
                           HavenMessageService messageService, MiniMessageSanitizer sanitizer) {
        super("join", settings.joinPresets(), playerService, messageService, sanitizer);
    }
}
```

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/QuitMsgCommand.java
package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;

public class QuitMsgCommand extends AbstractMsgCommand {
    public QuitMsgCommand(MessageSettings settings, HavenPlayerService playerService,
                           HavenMessageService messageService, MiniMessageSanitizer sanitizer) {
        super("quit", settings.quitPresets(), playerService, messageService, sanitizer);
    }
}
```

```java
// haven-core/src/main/java/dev/invisiblespiders/haven/core/message/AfkMsgCommand.java
package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;

public class AfkMsgCommand extends AbstractMsgCommand {
    public AfkMsgCommand(MessageSettings settings, HavenPlayerService playerService,
                          HavenMessageService messageService, MiniMessageSanitizer sanitizer) {
        super("afk", settings.afkPresets(), playerService, messageService, sanitizer);
    }
}
```

- [ ] **Step 3: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/AbstractMsgCommand.java
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/JoinMsgCommand.java
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/QuitMsgCommand.java
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/message/AfkMsgCommand.java
git commit -m "feat: add /joinmsg, /quitmsg, /afkmsg commands with set/select/clear subcommands"
```

---

## Task 8: Wire custom messages into HavenCore + ConfigManager + plugin.yml

**Files:**
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/config/ConfigManager.java`
- Modify: `haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java`
- Modify: `haven-core/src/main/resources/config.yml`
- Modify: `haven-core/src/main/resources/plugin.yml`

- [ ] **Step 1: Extend ConfigManager with messages.yml**

Add `messages.yml` to `CONFIG_FILES`, add `private FileConfiguration messages2` field (named `messages2` to avoid conflict with existing `messages` field used by `getMessage()`), load in `reload()`, add `public FileConfiguration getMessages2() { return messages2; }`.

Note: The existing `messages` field in `ConfigManager` is for HavenCore's own system messages (e.g. permission denied). The new `messages.yml` is for the custom message system. Avoid naming collision.

- [ ] **Step 2: Add feature toggle to config.yml**

```yaml
features:
  # ... existing ...
  custom-messages: true   # ← add
```

- [ ] **Step 3: Add commands and permissions to plugin.yml**

```yaml
commands:
  joinmsg:
    description: Set or select your custom join message
    usage: /joinmsg set|select|clear [value]
  quitmsg:
    description: Set or select your custom quit message
    usage: /quitmsg set|select|clear [value]
  afkmsg:
    description: Set or select your custom AFK message
    usage: /afkmsg set|select|clear [value]

permissions:
  haven.messages.custom:
    description: Set personal join/quit/afk messages with /joinmsg|quitmsg|afkmsg set
    default: true
  haven.messages.bypass-filter:
    description: Bypass MiniMessage tag and regex filter when setting custom messages
    default: op
  haven.messages.premium:
    description: Access premium death message pool
    default: false
  haven.admin.messages:
    description: Unlock message presets for other players via /haven messages unlock
    default: op
```

- [ ] **Step 4: Wire into HavenCore.onEnable()**

```java
PlayerMessageService playerMessageService = null;
if (configManager.getMain().getBoolean("features.custom-messages", true)) {
    MessageSettings messageSettings = MessageSettings.from(configManager.getMessages2());
    MiniMessageSanitizer sanitizer = new MiniMessageSanitizer(messageSettings.filter());
    playerMessageService = new PlayerMessageService(messageSettings, playerService, sanitizer);

    getServer().getPluginManager().registerEvents(playerMessageService, this);
    getServer().getPluginManager().registerEvents(
            new PlayerMessageListener(playerMessageService), this);

    sm.register(HavenMessageService.class, playerMessageService, this, ServicePriority.Normal);

    var joinMsgCmd = getCommand("joinmsg");
    if (joinMsgCmd != null) {
        var cmd = new JoinMsgCommand(messageSettings, playerService, playerMessageService, sanitizer);
        joinMsgCmd.setExecutor(cmd); joinMsgCmd.setTabCompleter(cmd);
    }
    var quitMsgCmd = getCommand("quitmsg");
    if (quitMsgCmd != null) {
        var cmd = new QuitMsgCommand(messageSettings, playerService, playerMessageService, sanitizer);
        quitMsgCmd.setExecutor(cmd); quitMsgCmd.setTabCompleter(cmd);
    }
    var afkMsgCmd = getCommand("afkmsg");
    if (afkMsgCmd != null) {
        var cmd = new AfkMsgCommand(messageSettings, playerService, playerMessageService, sanitizer);
        afkMsgCmd.setExecutor(cmd); afkMsgCmd.setTabCompleter(cmd);
    }
    getLogger().info("Custom message system enabled.");
}

// Late-inject into AfkManager so AFK broadcasts use custom messages
if (afkManager != null && playerMessageService != null) {
    afkManager.setMessageService(playerMessageService);
}
```

Add required imports:
```java
import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.core.message.*;
```

- [ ] **Step 5: Run full test suite**

```
.\gradlew test -i
```
Expected: PASS — all tests green.

- [ ] **Step 6: Build JAR**

```
.\gradlew :haven-core:shadowJar
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/config/ConfigManager.java
git add haven-core/src/main/java/dev/invisiblespiders/haven/core/HavenCore.java
git add haven-core/src/main/resources/config.yml
git add haven-core/src/main/resources/plugin.yml
git commit -m "feat: wire custom messages system into HavenCore with AFK integration"
```
