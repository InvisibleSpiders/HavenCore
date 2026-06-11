package dev.invisiblespiders.haven.api.service;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface HavenEconomyService {

    /** Returns true if money economy (VaultUnlocked) is available. */
    boolean isMoneyAvailable();

    /** Returns true if item economy is configured and available. */
    boolean isItemAvailable();

    /** Returns the configured preferred adapter id: "money" or "item". */
    String getPreferredAdapter();

    double getBalance(UUID uuid);

    /** Returns true and deducts if sufficient funds. False if insufficient. */
    boolean withdraw(UUID uuid, double amount);

    boolean deposit(UUID uuid, double amount);

    boolean has(UUID uuid, double amount);

    /** Formats the amount using the active economy's format. */
    String format(double amount);

    /**
     * Configures which currency the money adapter uses.
     * Has no effect on adapters that don't support multi-currency (e.g. VaultUnlocked).
     */
    void setMoneyCurrencyId(String currencyId);

    /** Item economy: count currency items in player's inventory. */
    int getItemBalance(Player player);

    /** Item economy: remove up to count currency items. Returns amount removed. */
    int withdrawItems(Player player, int count);

    /** Item economy: give currency items to player. */
    void depositItems(Player player, int count);
}
