package dev.invisiblespiders.haven.core.upgrade;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeCategory;
import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeEffect;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradeProvider;
import dev.invisiblespiders.haven.api.upgrade.UpgradePurchaseResult;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirementResult;
import dev.invisiblespiders.haven.api.upgrade.UpgradeScope;
import dev.invisiblespiders.haven.api.upgrade.UpgradeVisibility;
import dev.invisiblespiders.haven.core.db.SqlMigrator;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpgradeServiceImplTest {

    private HikariDataSource dataSource;
    private UpgradeRepository repository;
    private Player player;

    @BeforeEach
    void setup() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        SqlMigrator.migrate(dataSource, "haven", "db/migrations/haven", getClass().getClassLoader());
        repository = new UpgradeRepository(dataSource);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(true);
    }

    @AfterEach
    void teardown() {
        dataSource.close();
    }

    @Test
    void purchaseConsumesRequirementAppliesEffectAndRecordsLevel() {
        TestRequirement requirement = new TestRequirement(true, true);
        TestEffect effect = new TestEffect(true, true);
        UpgradeDefinition definition = TestUpgrades.playerTrack("test:slots", requirement, effect);
        UpgradeServiceImpl service = serviceWith(definition);

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertTrue(result.succeeded());
        assertEquals(1, repository.currentLevel(player.getUniqueId(), "test:slots"));
        assertTrue(requirement.consumed());
        assertTrue(effect.applied());
    }

    @Test
    void failedEffectRefundsConsumedRequirement() {
        TestRequirement requirement = new TestRequirement(true, true);
        TestEffect effect = new TestEffect(true, false);
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots", requirement, effect));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("effect-failed", result.code());
        assertTrue(requirement.refunded());
    }

    @Test
    void duplicateProviderIdIsRejected() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));

        assertThrows(IllegalArgumentException.class,
                () -> service.registerProvider(TestUpgrades.provider("test-provider",
                        TestUpgrades.playerTrack("test:other"))));
    }

    @Test
    void duplicateUpgradeIdAcrossProvidersIsRejected() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));

        assertThrows(IllegalArgumentException.class,
                () -> service.registerProvider(TestUpgrades.provider("other-provider",
                        TestUpgrades.definition("test:slots", "other-provider", UpgradeVisibility.VISIBLE))));
    }

    @Test
    void unknownUpgradeReturnsUnknownUpgrade() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));

        UpgradePurchaseResult result = service.purchase(player, "test:missing");

        assertFalse(result.succeeded());
        assertEquals("unknown-upgrade", result.code());
    }

    @Test
    void hiddenUpgradeReturnsHiddenUpgradeAndDoesNotConsumeOrApply() {
        TestRequirement requirement = new TestRequirement(true, true);
        TestEffect effect = new TestEffect(true, true);
        UpgradeServiceImpl service = serviceWith(
                TestUpgrades.definition("test:slots", "test-provider", UpgradeVisibility.HIDDEN, null,
                        TestUpgrades.level(1, requirement, effect)));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("hidden-upgrade", result.code());
        assertFalse(requirement.consumed());
        assertFalse(effect.applied());
    }

    @Test
    void lockedUpgradeReturnsLockedUpgradeAndDoesNotConsumeOrApply() {
        TestRequirement requirement = new TestRequirement(true, true);
        TestEffect effect = new TestEffect(true, true);
        UpgradeServiceImpl service = serviceWith(
                TestUpgrades.definition("test:slots", "test-provider", UpgradeVisibility.LOCKED, null,
                        TestUpgrades.level(1, requirement, effect)));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("locked-upgrade", result.code());
        assertFalse(requirement.consumed());
        assertFalse(effect.applied());
    }

    @Test
    void maxLevelReturnsMaxLevel() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));
        service.purchase(player, "test:slots");

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("max-level", result.code());
    }

    @Test
    void noPermissionReturnsNoPermission() {
        when(player.hasPermission("haven.upgrade.test")).thenReturn(false);
        UpgradeServiceImpl service = serviceWith(
                TestUpgrades.definition("test:slots", "test-provider", UpgradeVisibility.VISIBLE,
                        "haven.upgrade.test", TestUpgrades.level(1)));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("no-permission", result.code());
    }

    @Test
    void failedRequirementValidationReturnsRequirementNotMetAndDoesNotConsume() {
        TestRequirement requirement = new TestRequirement(false, true);
        TestEffect effect = new TestEffect(true, true);
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots", requirement, effect));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("requirement-not-met", result.code());
        assertFalse(requirement.consumed());
        assertFalse(effect.applied());
    }

    @Test
    void failedRequirementConsumeReturnsRequirementConsumeFailedAndRefundsPriorConsumedRequirements() {
        TestRequirement first = new TestRequirement(true, true);
        TestRequirement second = new TestRequirement(true, false);
        TestEffect effect = new TestEffect(true, true);
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots",
                List.of(first, second), List.of(effect)));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("requirement-consume-failed", result.code());
        assertTrue(first.refunded());
        assertFalse(effect.applied());
    }

    @Test
    void failedEffectValidationReturnsEffectInvalidAndDoesNotConsumeRequirements() {
        TestRequirement requirement = new TestRequirement(true, true);
        TestEffect effect = new TestEffect(false, true);
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots", requirement, effect));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("effect-invalid", result.code());
        assertFalse(requirement.consumed());
        assertFalse(effect.applied());
    }

    @Test
    void successfulSecondPurchaseBuysLevelTwoAndRecordsCurrentLevelTwo() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots",
                List.of(TestUpgrades.level(1), TestUpgrades.level(2))));

        assertTrue(service.purchase(player, "test:slots").succeeded());
        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertTrue(result.succeeded());
        assertEquals(2, repository.currentLevel(player.getUniqueId(), "test:slots"));
    }

    @Test
    void failedRecordPurchaseRollsBackAppliedEffectsAndRefundsConsumedRequirements() {
        TestRequirement requirement = new TestRequirement(true, true);
        TestEffect effect = new TestEffect(true, true);
        UUID playerId = player.getUniqueId();
        UpgradeRepository failingRepository = mock(UpgradeRepository.class);
        when(failingRepository.currentLevel(playerId, "test:slots")).thenReturn(0);
        doThrow(new IllegalStateException("record failed"))
                .when(failingRepository)
                .recordPurchase(anyString(), anyString(), any(UUID.class), anyString(),
                        anyInt(), any(UUID.class), anyString());
        UpgradeServiceImpl service = serviceWith(failingRepository,
                TestUpgrades.playerTrack("test:slots", requirement, effect));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("record-failed", result.code());
        assertTrue(effect.rolledBack());
        assertTrue(requirement.refunded());
    }

    @Test
    void unregisterProviderRemovesDefinitionsAndSnapshotsRemainStable() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));
        List<UpgradeProvider> providers = service.providers();
        List<UpgradeDefinition> definitions = service.definitions();

        service.unregisterProvider("test-provider");

        assertEquals(1, providers.size());
        assertEquals(1, definitions.size());
        assertTrue(service.providers().isEmpty());
        assertTrue(service.definitions().isEmpty());
        assertTrue(service.findDefinition("test:slots").isEmpty());
    }

    @Test
    void grantAndRevokeRecordAndRemoveRequestedLevel() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));
        UUID targetPlayerId = player.getUniqueId();

        UpgradePurchaseResult grant = service.grant(targetPlayerId, "test:slots", 3, "admin");
        UpgradePurchaseResult revoke = service.revoke(targetPlayerId, "test:slots", "admin");

        assertTrue(grant.succeeded());
        assertTrue(revoke.succeeded());
        assertEquals(0, repository.currentLevel(targetPlayerId, "test:slots"));
        assertEquals(1, repository.history(targetPlayerId).size());
    }

    private UpgradeServiceImpl serviceWith(UpgradeDefinition definition) {
        return serviceWith(repository, definition);
    }

    private UpgradeServiceImpl serviceWith(UpgradeRepository repository, UpgradeDefinition definition) {
        UpgradeServiceImpl service = new UpgradeServiceImpl(repository);
        service.registerProvider(TestUpgrades.provider(definition.providerId(), definition));
        return service;
    }

    private static final class TestRequirement implements UpgradeRequirement {
        private final boolean validates;
        private final boolean consumes;
        private boolean consumed;
        private boolean refunded;

        private TestRequirement(boolean validates, boolean consumes) {
            this.validates = validates;
            this.consumes = consumes;
        }

        @Override
        public String type() {
            return "test-requirement";
        }

        @Override
        public UpgradeRequirementResult validate(UpgradeContext context) {
            return validates
                    ? UpgradeRequirementResult.success()
                    : UpgradeRequirementResult.failure("test-requirement-failed", "Requirement failed.");
        }

        @Override
        public void consume(UpgradeContext context) {
            if (!consumes) {
                throw new IllegalStateException("consume failed");
            }
            consumed = true;
        }

        @Override
        public void refund(UpgradeContext context) {
            refunded = true;
        }

        private boolean consumed() {
            return consumed;
        }

        private boolean refunded() {
            return refunded;
        }
    }

    private static final class TestEffect implements UpgradeEffect {
        private final boolean validates;
        private final boolean applies;
        private boolean applied;
        private boolean rolledBack;

        private TestEffect(boolean validates, boolean applies) {
            this.validates = validates;
            this.applies = applies;
        }

        @Override
        public String type() {
            return "test-effect";
        }

        @Override
        public UpgradeRequirementResult validate(UpgradeContext context) {
            return validates
                    ? UpgradeRequirementResult.success()
                    : UpgradeRequirementResult.failure("test-effect-invalid", "Effect failed.");
        }

        @Override
        public void apply(UpgradeContext context) {
            if (!applies) {
                throw new IllegalStateException("apply failed");
            }
            applied = true;
        }

        @Override
        public void rollback(UpgradeContext context) {
            rolledBack = true;
        }

        private boolean applied() {
            return applied;
        }

        private boolean rolledBack() {
            return rolledBack;
        }
    }

    private static final class TestUpgrades {
        private static final UpgradeCategory CATEGORY = new UpgradeCategory("storage", "Storage", "CHEST", 0);

        private static UpgradeDefinition playerTrack(String id) {
            return playerTrack(id, new TestRequirement(true, true), new TestEffect(true, true));
        }

        private static UpgradeDefinition playerTrack(String id, TestRequirement requirement, TestEffect effect) {
            return playerTrack(id, List.of(requirement), List.of(effect));
        }

        private static UpgradeDefinition playerTrack(
                String id, List<UpgradeRequirement> requirements, List<UpgradeEffect> effects) {
            return definition(id, "test-provider", UpgradeVisibility.VISIBLE, null,
                    new UpgradeLevel(1, "Level 1", requirements, effects, Map.of("slots", "9")));
        }

        private static UpgradeDefinition playerTrack(String id, List<UpgradeLevel> levels) {
            return new UpgradeDefinition(id, "test-provider", CATEGORY, UpgradeScope.PLAYER,
                    UpgradeVisibility.VISIBLE, null, levels);
        }

        private static UpgradeDefinition definition(String id, String providerId, UpgradeVisibility visibility) {
            return definition(id, providerId, visibility, null, level(1));
        }

        private static UpgradeDefinition definition(String id, String providerId, UpgradeVisibility visibility,
                                                    String permission, UpgradeLevel level) {
            return new UpgradeDefinition(id, providerId, CATEGORY, UpgradeScope.PLAYER, visibility, permission,
                    List.of(level));
        }

        private static UpgradeLevel level(int level) {
            return level(level, new TestRequirement(true, true), new TestEffect(true, true));
        }

        private static UpgradeLevel level(int level, UpgradeRequirement requirement, UpgradeEffect effect) {
            return new UpgradeLevel(level, "Level " + level, List.of(requirement), List.of(effect),
                    Map.of("level", Integer.toString(level)));
        }

        private static UpgradeProvider provider(String id, UpgradeDefinition definition) {
            return new UpgradeProvider() {
                @Override
                public String id() {
                    return id;
                }

                @Override
                public String displayName() {
                    return "Test Provider";
                }

                @Override
                public List<UpgradeCategory> categories() {
                    return List.of(CATEGORY);
                }

                @Override
                public List<UpgradeDefinition> definitions() {
                    return List.of(definition);
                }

                @Override
                public Optional<UpgradeEffect> effect(String type, Map<String, String> values) {
                    return Optional.empty();
                }

                @Override
                public Optional<UpgradeRequirement> requirement(String type, Map<String, String> values) {
                    return Optional.empty();
                }
            };
        }
    }
}
