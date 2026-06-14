package dev.invisiblespiders.haven.core.upgrade;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
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
import dev.invisiblespiders.haven.api.upgrade.UpgradeViewRequest;
import dev.invisiblespiders.haven.api.upgrade.UpgradeVisibility;
import dev.invisiblespiders.haven.core.dialog.UpgradeDialog;
import dev.invisiblespiders.haven.core.db.SqlMigrator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        UpgradePurchaseRecord record = repository.history(player.getUniqueId()).getFirst();
        assertEquals("purchase", record.source());
        assertEquals(1, record.purchasedLevel());
        assertEquals(player.getUniqueId(), record.purchaserId());
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
    void mismatchedDefinitionProviderIdIsRejectedWithoutStaleDefinitions() {
        UpgradeServiceImpl service = new UpgradeServiceImpl(repository);
        UpgradeDefinition mismatched = TestUpgrades.definition(
                "test:slots", "other-provider", UpgradeVisibility.VISIBLE);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerProvider(TestUpgrades.provider("test-provider", mismatched)));

        assertTrue(service.providers().isEmpty());
        assertTrue(service.definitions().isEmpty());
        assertTrue(service.findDefinition("test:slots").isEmpty());
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
    void failedRequirementConsumeRefundsPriorConsumedRequirementsInReverseOrder() {
        List<String> operations = new ArrayList<>();
        OrderedRequirement first = new OrderedRequirement("first", operations, true);
        OrderedRequirement second = new OrderedRequirement("second", operations, true);
        OrderedRequirement third = new OrderedRequirement("third", operations, false);
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots",
                List.of(first, second, third), List.of(new TestEffect(true, true))));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals(List.of("consume:first", "consume:second", "consume:third", "refund:second", "refund:first"),
                operations);
    }

    @Test
    void failedRequirementConsumeContinuesRefundsWhenOneRefundThrowsAndPreservesOriginalCode() {
        List<String> operations = new ArrayList<>();
        CleanupRequirement first = new CleanupRequirement("first", operations, true, false);
        CleanupRequirement second = new CleanupRequirement("second", operations, true, false);
        CleanupRequirement third = new CleanupRequirement("third", operations, true, true);
        CleanupRequirement fourth = new CleanupRequirement("fourth", operations, false, false);
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots",
                List.of(first, second, third, fourth), List.of(new TestEffect(true, true))));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("requirement-consume-failed", result.code());
        assertEquals(List.of(
                "consume:first",
                "consume:second",
                "consume:third",
                "consume:fourth",
                "refund:third",
                "refund:second",
                "refund:first"
        ), operations);
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
    void failedEffectApplyRollsBackAppliedEffectsInReverseOrder() {
        List<String> operations = new ArrayList<>();
        OrderedEffect first = new OrderedEffect("first", operations, true);
        OrderedEffect second = new OrderedEffect("second", operations, true);
        OrderedEffect third = new OrderedEffect("third", operations, false);
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots",
                List.of(new TestRequirement(true, true)), List.of(first, second, third)));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals(List.of("apply:first", "apply:second", "apply:third", "rollback:second", "rollback:first"),
                operations);
    }

    @Test
    void failedEffectApplyContinuesRollbackAndRefundWhenOneRollbackThrowsAndPreservesOriginalCode() {
        List<String> operations = new ArrayList<>();
        CleanupRequirement requirement = new CleanupRequirement("requirement", operations, true, false);
        CleanupEffect first = new CleanupEffect("first", operations, true, false);
        CleanupEffect second = new CleanupEffect("second", operations, true, true);
        CleanupEffect third = new CleanupEffect("third", operations, true, false);
        CleanupEffect fourth = new CleanupEffect("fourth", operations, false, false);
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots",
                List.of(requirement), List.of(first, second, third, fourth)));

        UpgradePurchaseResult result = service.purchase(player, "test:slots");

        assertFalse(result.succeeded());
        assertEquals("effect-failed", result.code());
        assertEquals(List.of(
                "consume:requirement",
                "apply:first",
                "apply:second",
                "apply:third",
                "apply:fourth",
                "rollback:third",
                "rollback:second",
                "rollback:first",
                "refund:requirement"
        ), operations);
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
    void currentLevelDelegatesToRepository() {
        UpgradeServiceImpl service = new UpgradeServiceImpl(repository);
        UUID playerId = player.getUniqueId();
        repository.recordPurchase("test-provider", "test:slots", playerId, "PLAYER", 4, playerId, "fixture");

        assertEquals(4, service.currentLevel(playerId, "test:slots"));
    }

    @Test
    void openDialogDelegatesToConfiguredDialog() {
        UpgradeDialog dialog = mock(UpgradeDialog.class);
        UpgradeServiceImpl service = new UpgradeServiceImpl(repository);
        service.setDialog(dialog);
        UpgradeViewRequest request = UpgradeViewRequest.all();

        service.openDialog(player, request);

        verify(dialog).open(player, request);
    }

    @Test
    void purchaseWithoutScopeRecordsPlayerScope() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));

        service.purchase(player, "test:slots");

        assertEquals("PLAYER", repository.history(player.getUniqueId()).getFirst().targetScope());
    }

    @Test
    void purchaseWithExplicitScopeRecordsThatScope() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));

        service.purchase(player, "test:slots", UpgradeScope.ONLINE_ELIGIBLE_PLAYERS);

        assertEquals("ONLINE_ELIGIBLE_PLAYERS", repository.history(player.getUniqueId()).getFirst().targetScope());
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

        UpgradePurchaseResult grant = service.grant(targetPlayerId, "test:slots", 1, "admin");
        UpgradePurchaseResult revoke = service.revoke(targetPlayerId, "test:slots", "admin");

        assertTrue(grant.succeeded());
        assertTrue(revoke.succeeded());
        assertEquals(0, repository.currentLevel(targetPlayerId, "test:slots"));
        assertEquals(1, repository.history(targetPlayerId).size());
    }

    @Test
    void grantRejectsLevelsOutsideDefinition() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots",
                List.of(TestUpgrades.level(1), TestUpgrades.level(2))));
        UUID targetPlayerId = player.getUniqueId();

        UpgradePurchaseResult zero = service.grant(targetPlayerId, "test:slots", 0, "admin");
        UpgradePurchaseResult aboveMax = service.grant(targetPlayerId, "test:slots", 3, "admin");

        assertFalse(zero.succeeded());
        assertEquals("unknown-level", zero.code());
        assertFalse(aboveMax.succeeded());
        assertEquals("unknown-level", aboveMax.code());
        assertEquals(0, repository.currentLevel(targetPlayerId, "test:slots"));
        assertTrue(repository.history(targetPlayerId).isEmpty());
    }

    @Test
    void grantUnknownUpgradeReturnsUnknownUpgrade() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));

        UpgradePurchaseResult result = service.grant(UUID.randomUUID(), "test:missing", 1, "admin");

        assertFalse(result.succeeded());
        assertEquals("unknown-upgrade", result.code());
    }

    @Test
    void revokeUnknownUpgradeReturnsUnknownUpgrade() {
        UpgradeServiceImpl service = serviceWith(TestUpgrades.playerTrack("test:slots"));

        UpgradePurchaseResult result = service.revoke(UUID.randomUUID(), "test:missing", "admin");

        assertFalse(result.succeeded());
        assertEquals("unknown-upgrade", result.code());
    }

    @Test
    void findDefinitionReturnsRegisteredDefinitionAndDefinitionsSnapshotRemainsStableAfterUnregister() {
        UpgradeDefinition definition = TestUpgrades.playerTrack("test:slots");
        UpgradeServiceImpl service = serviceWith(definition);
        List<UpgradeDefinition> snapshot = service.definitions();

        assertEquals(Optional.of(definition), service.findDefinition("test:slots"));
        service.unregisterProvider("test-provider");

        assertEquals(List.of(definition), snapshot);
        assertTrue(service.definitions().isEmpty());
    }

    @Test
    void moneyRequirementRejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> new MoneyRequirement(mock(HavenEconomyService.class), -0.01));
    }

    @Test
    void moneyRequirementValidatesConsumesAndRefundsThroughEconomyService() {
        HavenEconomyService economy = mock(HavenEconomyService.class);
        UUID playerId = player.getUniqueId();
        when(economy.has(playerId, 25.0)).thenReturn(true);
        when(economy.withdraw(playerId, 25.0)).thenReturn(true);
        MoneyRequirement requirement = new MoneyRequirement(economy, 25.0);
        UpgradeContext context = contextFor(player);

        assertTrue(requirement.validate(context).satisfied());
        requirement.consume(context);
        requirement.refund(context);

        verify(economy).has(playerId, 25.0);
        verify(economy).withdraw(playerId, 25.0);
        verify(economy).deposit(playerId, 25.0);
    }

    @Test
    void itemRequirementRejectsNullMaterialAndNonPositiveAmount() {
        assertThrows(NullPointerException.class, () -> new ItemRequirement(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new ItemRequirement(Material.DIAMOND, 0));
        assertThrows(IllegalArgumentException.class, () -> new ItemRequirement(Material.DIAMOND, -1));
    }

    @Test
    void itemRequirementValidatesInventoryHasEnoughMaterial() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getContents()).thenReturn(new ItemStack[]{
                stack(Material.DIAMOND, 2),
                stack(Material.EMERALD, 10),
                stack(Material.DIAMOND, 3)
        });
        ItemRequirement requirement = new ItemRequirement(Material.DIAMOND, 5);

        assertTrue(requirement.validate(contextFor(player)).satisfied());

        when(inventory.getContents()).thenReturn(new ItemStack[]{stack(Material.DIAMOND, 4)});
        assertFalse(requirement.validate(contextFor(player)).satisfied());
    }

    @Test
    void itemRequirementConsumeRemovesMatchingMaterialItems() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack first = stack(Material.DIAMOND, 2);
        ItemStack second = stack(Material.EMERALD, 10);
        ItemStack third = stack(Material.DIAMOND, 4);
        ItemStack[] contents = {
                first,
                second,
                third
        };
        when(inventory.getContents()).thenReturn(contents);
        ItemRequirement requirement = new ItemRequirement(Material.DIAMOND, 5);

        requirement.consume(contextFor(player));

        ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
        verify(inventory).setContents(captor.capture());
        ItemStack[] updated = captor.getValue();
        assertEquals(null, updated[0]);
        assertEquals(Material.EMERALD, updated[1].getType());
        assertSame(third, updated[2]);
        assertEquals(1, updated[2].getAmount());
    }

    @Test
    void itemRequirementRefundGivesItemsBack() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemRequirement requirement = new ItemRequirement(Material.DIAMOND, 5);
        List<List<?>> constructorArguments = new ArrayList<>();

        try (MockedConstruction<ItemStack> ignored = mockConstruction(ItemStack.class,
                (mock, context) -> constructorArguments.add(context.arguments()))) {
            requirement.refund(contextFor(player));
        }

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory).addItem(captor.capture());
        assertEquals(Material.DIAMOND, constructorArguments.getFirst().get(0));
        assertEquals(5, constructorArguments.getFirst().get(1));
    }

    @Test
    void expRequirementRejectsNonPositiveLevels() {
        assertThrows(IllegalArgumentException.class, () -> new ExpRequirement(0));
        assertThrows(IllegalArgumentException.class, () -> new ExpRequirement(-1));
    }

    @Test
    void expRequirementValidatesConsumesAndRefundsPlayerLevels() {
        ExpRequirement requirement = new ExpRequirement(5);
        when(player.getLevel()).thenReturn(7);

        assertTrue(requirement.validate(contextFor(player)).satisfied());

        requirement.consume(contextFor(player));
        verify(player).setLevel(2);

        when(player.getLevel()).thenReturn(2);
        requirement.refund(contextFor(player));
        verify(player).setLevel(7);

        when(player.getLevel()).thenReturn(4);
        assertFalse(requirement.validate(contextFor(player)).satisfied());
    }

    @Test
    void permissionRequirementValidatesPermissionAndConsumeRefundAreNoOps() {
        PermissionRequirement requirement = new PermissionRequirement("haven.test");
        Player permissionPlayer = mock(Player.class);
        when(permissionPlayer.hasPermission("haven.test")).thenReturn(true, false);
        UpgradeContext context = new UpgradeContext(permissionPlayer, UUID.randomUUID(), "test:slots", 1,
                UpgradeScope.PLAYER, Map.of());

        assertTrue(requirement.validate(context).satisfied());
        assertFalse(requirement.validate(context).satisfied());

        clearInvocations(permissionPlayer);
        requirement.consume(context);
        requirement.refund(context);
        verifyNoInteractions(permissionPlayer);
    }

    private UpgradeServiceImpl serviceWith(UpgradeDefinition definition) {
        return serviceWith(repository, definition);
    }

    private UpgradeServiceImpl serviceWith(UpgradeRepository repository, UpgradeDefinition definition) {
        UpgradeServiceImpl service = new UpgradeServiceImpl(repository);
        service.registerProvider(TestUpgrades.provider(definition.providerId(), definition));
        return service;
    }

    private UpgradeContext contextFor(Player player) {
        return new UpgradeContext(player, player.getUniqueId(), "test:slots", 1, UpgradeScope.PLAYER, Map.of());
    }

    private ItemStack stack(Material material, int amount) {
        return new TestItemStack(material, amount);
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

    private static final class OrderedRequirement implements UpgradeRequirement {
        private final String name;
        private final List<String> operations;
        private final boolean consumes;

        private OrderedRequirement(String name, List<String> operations, boolean consumes) {
            this.name = name;
            this.operations = operations;
            this.consumes = consumes;
        }

        @Override
        public String type() {
            return "ordered-requirement";
        }

        @Override
        public UpgradeRequirementResult validate(UpgradeContext context) {
            return UpgradeRequirementResult.success();
        }

        @Override
        public void consume(UpgradeContext context) {
            operations.add("consume:" + name);
            if (!consumes) {
                throw new IllegalStateException("consume failed");
            }
        }

        @Override
        public void refund(UpgradeContext context) {
            operations.add("refund:" + name);
        }
    }

    private static final class CleanupRequirement implements UpgradeRequirement {
        private final String name;
        private final List<String> operations;
        private final boolean consumes;
        private final boolean refundThrows;

        private CleanupRequirement(String name, List<String> operations, boolean consumes, boolean refundThrows) {
            this.name = name;
            this.operations = operations;
            this.consumes = consumes;
            this.refundThrows = refundThrows;
        }

        @Override
        public String type() {
            return "cleanup-requirement";
        }

        @Override
        public UpgradeRequirementResult validate(UpgradeContext context) {
            return UpgradeRequirementResult.success();
        }

        @Override
        public void consume(UpgradeContext context) {
            operations.add("consume:" + name);
            if (!consumes) {
                throw new IllegalStateException("consume failed");
            }
        }

        @Override
        public void refund(UpgradeContext context) {
            operations.add("refund:" + name);
            if (refundThrows) {
                throw new IllegalStateException("refund failed");
            }
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

    private static final class OrderedEffect implements UpgradeEffect {
        private final String name;
        private final List<String> operations;
        private final boolean applies;

        private OrderedEffect(String name, List<String> operations, boolean applies) {
            this.name = name;
            this.operations = operations;
            this.applies = applies;
        }

        @Override
        public String type() {
            return "ordered-effect";
        }

        @Override
        public void apply(UpgradeContext context) {
            operations.add("apply:" + name);
            if (!applies) {
                throw new IllegalStateException("apply failed");
            }
        }

        @Override
        public void rollback(UpgradeContext context) {
            operations.add("rollback:" + name);
        }
    }

    private static final class CleanupEffect implements UpgradeEffect {
        private final String name;
        private final List<String> operations;
        private final boolean applies;
        private final boolean rollbackThrows;

        private CleanupEffect(String name, List<String> operations, boolean applies, boolean rollbackThrows) {
            this.name = name;
            this.operations = operations;
            this.applies = applies;
            this.rollbackThrows = rollbackThrows;
        }

        @Override
        public String type() {
            return "cleanup-effect";
        }

        @Override
        public void apply(UpgradeContext context) {
            operations.add("apply:" + name);
            if (!applies) {
                throw new IllegalStateException("apply failed");
            }
        }

        @Override
        public void rollback(UpgradeContext context) {
            operations.add("rollback:" + name);
            if (rollbackThrows) {
                throw new IllegalStateException("rollback failed");
            }
        }
    }

    private static final class TestItemStack extends ItemStack {
        private final Material material;
        private int amount;

        private TestItemStack(Material material, int amount) {
            this.material = material;
            this.amount = amount;
        }

        @Override
        public Material getType() {
            return material;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
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
