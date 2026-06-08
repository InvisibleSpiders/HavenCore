package dev.invisiblespiders.haven.api.model;

import org.bukkit.Material;

public record CodexEntry(
    String key,
    String displayName,   // MiniMessage
    Material icon,
    CodexCategory category,
    String description,   // MiniMessage
    CodexRarity rarity
) {
    public static Builder builder(String key, CodexCategory category, Material icon) {
        return new Builder(key, category, icon);
    }

    public static final class Builder {
        private final String key;
        private final CodexCategory category;
        private final Material icon;
        private String displayName = "";
        private String description = "";
        private CodexRarity rarity = CodexRarity.COMMON;

        private Builder(String key, CodexCategory category, Material icon) {
            this.key = key;
            this.category = category;
            this.icon = icon;
        }

        public Builder displayName(String name) { this.displayName = name; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder rarity(CodexRarity rarity) { this.rarity = rarity; return this; }

        public CodexEntry build() {
            return new CodexEntry(key, displayName, icon, category, description, rarity);
        }
    }
}
