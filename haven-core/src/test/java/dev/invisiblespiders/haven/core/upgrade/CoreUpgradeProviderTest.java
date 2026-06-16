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
