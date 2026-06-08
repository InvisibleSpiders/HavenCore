package dev.invisiblespiders.haven.core.economy;

import dev.invisiblespiders.haven.api.model.EconomyAdapter;
import dev.invisiblespiders.haven.core.hook.VaultUnlockedHook;
import net.milkbowl.vault2.economy.Economy;

import java.util.UUID;

public class MoneyEconomyAdapter implements EconomyAdapter {

    private final VaultUnlockedHook hook;

    public MoneyEconomyAdapter(VaultUnlockedHook hook) {
        this.hook = hook;
    }

    @Override
    public boolean isAvailable() { return hook.isAvailable(); }

    private Economy eco() { return hook.getEconomy(); }

    @Override
    public double getBalance(UUID uuid) {
        if (!isAvailable()) return 0;
        return eco().getBalance(uuid).doubleValue();
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        if (!isAvailable()) return false;
        return eco().has(uuid, amount);
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        if (!isAvailable() || !has(uuid, amount)) return false;
        eco().withdraw(uuid, amount);
        return true;
    }

    @Override
    public void deposit(UUID uuid, double amount) {
        if (isAvailable()) eco().deposit(uuid, amount);
    }

    @Override
    public String format(double amount) {
        if (!isAvailable()) return String.valueOf(amount);
        return eco().format(amount);
    }
}
