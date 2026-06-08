package dev.invisiblespiders.haven.api.model;

import java.util.UUID;

public interface EconomyAdapter {

    boolean isAvailable();

    double getBalance(UUID uuid);

    boolean has(UUID uuid, double amount);

    /** Returns true and deducts if sufficient balance. */
    boolean withdraw(UUID uuid, double amount);

    void deposit(UUID uuid, double amount);

    String format(double amount);
}
