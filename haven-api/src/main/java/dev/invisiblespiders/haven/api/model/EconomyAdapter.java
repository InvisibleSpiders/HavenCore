package dev.invisiblespiders.haven.api.model;

import java.util.UUID;

public interface EconomyAdapter {

    boolean isAvailable();

    double getBalance(UUID uuid);

    boolean has(UUID uuid, double amount);

    /** Returns true and deducts if sufficient balance. */
    boolean withdraw(UUID uuid, double amount);

    boolean deposit(UUID uuid, double amount);

    String format(double amount);

    /** Sets the currency identifier used by this adapter. No-op for adapters that don't support it. */
    default void setCurrencyId(String id) {}
}
