package dev.invisiblespiders.haven.api.upgrade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UpgradeApiModelTest {
    @Test
    void definitionRequiresNamespacedIdAndLevel() {
        UpgradeCategory category = new UpgradeCategory("bank", "Bank", "CHEST", 10);
        UpgradeLevel level = new UpgradeLevel(
                1,
                "Bank Cap I",
                List.of(),
                List.of(),
                Map.of("cap-increase", "1000")
        );

        UpgradeDefinition definition = new UpgradeDefinition(
                "havenvault:bank-cap",
                "havenvault",
                category,
                UpgradeScope.PLAYER,
                UpgradeVisibility.VISIBLE,
                "havenvault.upgrades.bank-cap",
                List.of(level)
        );

        assertEquals("havenvault:bank-cap", definition.id());
        assertEquals(1, definition.levels().size());
        assertEquals(UpgradeScope.PLAYER, definition.scope());
    }

    @Test
    void purchaseResultCarriesMachineCodeAndMessage() {
        UpgradePurchaseResult result = UpgradePurchaseResult.failure(
                "insufficient-funds",
                "You cannot afford this upgrade."
        );

        assertFalse(result.succeeded());
        assertEquals("insufficient-funds", result.code());
        assertEquals("You cannot afford this upgrade.", result.message());
    }

    @Test
    void definitionCopiesLevelsAndRejectsMutation() {
        UpgradeCategory category = new UpgradeCategory("bank", "Bank", "CHEST", 10);
        List<UpgradeLevel> levels = new ArrayList<>();
        levels.add(new UpgradeLevel(1, "Bank Cap I", List.of(), List.of(), Map.of()));

        UpgradeDefinition definition = new UpgradeDefinition(
                "havenvault:bank-cap",
                "havenvault",
                category,
                UpgradeScope.PLAYER,
                UpgradeVisibility.VISIBLE,
                null,
                levels
        );
        levels.add(new UpgradeLevel(2, "Bank Cap II", List.of(), List.of(), Map.of()));

        assertEquals(1, definition.levels().size());
        assertThrows(UnsupportedOperationException.class, () -> definition.levels().clear());
    }

    @Test
    void levelCopiesRequirementsEffectsAndMetadata() {
        List<UpgradeRequirement> requirements = new ArrayList<>();
        List<UpgradeEffect> effects = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("cap-increase", "1000");

        UpgradeLevel level = new UpgradeLevel(1, "Bank Cap I", requirements, effects, metadata);
        requirements.add(new NoopRequirement());
        effects.add(new NoopEffect());
        metadata.put("cap-increase", "2000");

        assertTrue(level.requirements().isEmpty());
        assertTrue(level.effects().isEmpty());
        assertEquals("1000", level.metadata().get("cap-increase"));
        assertThrows(UnsupportedOperationException.class, () -> level.requirements().add(new NoopRequirement()));
        assertThrows(UnsupportedOperationException.class, () -> level.effects().add(new NoopEffect()));
        assertThrows(UnsupportedOperationException.class, () -> level.metadata().put("other", "value"));
    }

    @Test
    void contextCopiesMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "test");

        UpgradeContext context = new UpgradeContext(
                null,
                UUID.randomUUID(),
                "havenvault:bank-cap",
                1,
                UpgradeScope.PLAYER,
                metadata
        );
        metadata.put("source", "mutated");

        assertEquals("test", context.metadata().get("source"));
        assertThrows(UnsupportedOperationException.class, () -> context.metadata().clear());
    }

    @Test
    void requiredUpgradeFieldsRejectNulls() {
        UpgradeCategory category = new UpgradeCategory("bank", "Bank", "CHEST", 10);

        assertThrows(NullPointerException.class, () -> new UpgradeCategory(null, "Bank", "CHEST", 10));
        assertThrows(NullPointerException.class, () -> new UpgradeLevel(1, "Bank Cap I", null, List.of(), Map.of()));
        assertThrows(NullPointerException.class, () -> new UpgradeDefinition(
                "havenvault:bank-cap",
                null,
                category,
                UpgradeScope.PLAYER,
                UpgradeVisibility.VISIBLE,
                null,
                List.of()
        ));
        assertThrows(NullPointerException.class, () -> new UpgradeContext(
                null,
                UUID.randomUUID(),
                "havenvault:bank-cap",
                1,
                UpgradeScope.PLAYER,
                null
        ));
    }

    @Test
    void providerEffectReceivesConfigurationValues() {
        UpgradeProvider provider = new UpgradeProvider() {
            @Override
            public String id() {
                return "havenvault";
            }

            @Override
            public String displayName() {
                return "HavenVault";
            }

            @Override
            public List<UpgradeCategory> categories() {
                return List.of();
            }

            @Override
            public List<UpgradeDefinition> definitions() {
                return List.of();
            }

            @Override
            public Optional<UpgradeEffect> effect(String type, Map<String, String> values) {
                return "bank-cap".equals(type) && "1000".equals(values.get("amount"))
                        ? Optional.of(new NoopEffect())
                        : Optional.empty();
            }

            @Override
            public Optional<UpgradeRequirement> requirement(String type, Map<String, String> values) {
                return Optional.empty();
            }
        };

        assertTrue(provider.effect("bank-cap", Map.of("amount", "1000")).isPresent());
    }

    @Test
    void viewRequestCopiesFiltersAndRejectsMutation() {
        Set<String> categories = new java.util.HashSet<>();
        categories.add("bank");

        UpgradeViewRequest request = UpgradeViewRequest.categories(categories);
        categories.add("storage");

        assertEquals(Set.of("bank"), request.categoryIds());
        assertThrows(UnsupportedOperationException.class, () -> request.categoryIds().add("storage"));
    }

    private static final class NoopRequirement implements UpgradeRequirement {
        @Override
        public String type() {
            return "noop";
        }

        @Override
        public UpgradeRequirementResult validate(UpgradeContext context) {
            return UpgradeRequirementResult.success();
        }

        @Override
        public void consume(UpgradeContext context) {
        }

        @Override
        public void refund(UpgradeContext context) {
        }
    }

    private static final class NoopEffect implements UpgradeEffect {
        @Override
        public String type() {
            return "noop";
        }

        @Override
        public void apply(UpgradeContext context) {
        }

        @Override
        public void rollback(UpgradeContext context) {
        }
    }
}
