package dev.invisiblespiders.haven.api.service;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface HavenEconomyService {

    /** Returns true if money economy (VaultUnlocked) is available. */
    boolean isMoneyAvailable();

    /** Returns true if item economy is configured and available. */
    boolean isItemAvailable();

    double getBalance(UUID uuid);

    /** Returns true and deducts if sufficient funds. False if insufficient. */
    boolean withdraw(UUID uuid, double amount);

    boolean deposit(UUID uuid, double amount);

    boolean has(UUID uuid, double amount);

    /** Formats the amount using the active economy's format. */
    String format(double amount);

    /** Item economy: count currency items in player's inventory. */
    int getItemBalance(Player player);

    /** Item economy: remove up to count currency items. Returns amount removed. */
    int withdrawItems(Player player, int count);

    /** Item economy: give currency items to player. */
    void depositItems(Player player, int count);
}
