package dev.invisiblespiders.haven.api.upgrade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
}
