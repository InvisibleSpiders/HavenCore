package dev.invisiblespiders.haven.core.economy;

import dev.invisiblespiders.haven.api.model.EconomyAdapter;
import dev.invisiblespiders.haven.core.hook.VaultUnlockedHook;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;

import java.math.BigDecimal;
import java.util.UUID;

public class MoneyEconomyAdapter implements EconomyAdapter {

    /** Identifies HavenCore as the calling plugin to VaultUnlocked. */
    private static final String PLUGIN_NAME = "HavenCore";

    private final VaultUnlockedHook hook;

    public MoneyEconomyAdapter(VaultUnlockedHook hook) {
        this.hook = hook;
    }

    @Override
    public boolean isAvailable() { return hook.hasEconomyProvider(); }

    private Economy eco() { return hook.getEconomy(); }

    @Override
    public double getBalance(UUID uuid) {
        if (!isAvailable()) return 0;
        BigDecimal bal = eco().balance(PLUGIN_NAME, uuid);
        return bal == null ? 0 : bal.doubleValue();
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        if (!isAvailable()) return false;
        return eco().has(PLUGIN_NAME, uuid, BigDecimal.valueOf(amount));
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        if (!isAvailable() || !has(uuid, amount)) return false;
        EconomyResponse response = eco().withdraw(PLUGIN_NAME, uuid, BigDecimal.valueOf(amount));
        return response != null && response.transactionSuccess();
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        if (!isAvailable()) return false;
        EconomyResponse response = eco().deposit(PLUGIN_NAME, uuid, BigDecimal.valueOf(amount));
        return response != null && response.transactionSuccess();
    }

    @Override
    public String format(double amount) {
        if (!isAvailable()) return String.valueOf(amount);
        return eco().format(PLUGIN_NAME, BigDecimal.valueOf(amount));
    }
}
