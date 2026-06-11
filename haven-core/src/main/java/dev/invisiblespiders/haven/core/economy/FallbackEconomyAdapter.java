package dev.invisiblespiders.haven.core.economy;

import dev.invisiblespiders.haven.api.model.EconomyAdapter;

import java.util.List;
import java.util.UUID;

/**
 * Delegates to the first available adapter in priority order.
 * Allows multiple economy backends (e.g. ExcellentEconomy, VaultUnlocked)
 * to coexist without changing the rest of the stack.
 */
public class FallbackEconomyAdapter implements EconomyAdapter {

    private final List<EconomyAdapter> adapters;

    public FallbackEconomyAdapter(EconomyAdapter... adapters) {
        this.adapters = List.of(adapters);
    }

    private EconomyAdapter active() {
        for (EconomyAdapter adapter : adapters) {
            if (adapter.isAvailable()) return adapter;
        }
        return null;
    }

    @Override
    public boolean isAvailable() { return active() != null; }

    @Override
    public double getBalance(UUID uuid) {
        EconomyAdapter a = active();
        return a != null ? a.getBalance(uuid) : 0;
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        EconomyAdapter a = active();
        return a != null && a.has(uuid, amount);
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        EconomyAdapter a = active();
        return a != null && a.withdraw(uuid, amount);
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        EconomyAdapter a = active();
        return a != null && a.deposit(uuid, amount);
    }

    @Override
    public String format(double amount) {
        EconomyAdapter a = active();
        return a != null ? a.format(amount) : String.valueOf(amount);
    }
}
