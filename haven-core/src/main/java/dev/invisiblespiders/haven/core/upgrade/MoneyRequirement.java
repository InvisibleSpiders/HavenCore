package dev.invisiblespiders.haven.core.upgrade;

import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirementResult;

import java.util.Objects;

public final class MoneyRequirement implements UpgradeRequirement {

    private final HavenEconomyService economy;
    private final double amount;

    public MoneyRequirement(HavenEconomyService economy, double amount) {
        this.economy = Objects.requireNonNull(economy, "economy");
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        this.amount = amount;
    }

    @Override
    public String type() {
        return "money";
    }

    @Override
    public UpgradeRequirementResult validate(UpgradeContext context) {
        return economy.has(context.targetPlayerId(), amount)
                ? UpgradeRequirementResult.success()
                : UpgradeRequirementResult.failure("insufficient-money", "Insufficient money.");
    }

    @Override
    public void consume(UpgradeContext context) {
        if (!economy.withdraw(context.targetPlayerId(), amount)) {
            throw new IllegalStateException("money withdrawal failed");
        }
    }

    @Override
    public void refund(UpgradeContext context) {
        economy.deposit(context.targetPlayerId(), amount);
    }
}
