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
