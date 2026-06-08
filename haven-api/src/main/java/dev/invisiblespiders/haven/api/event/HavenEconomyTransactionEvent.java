package dev.invisiblespiders.haven.api.event;

import java.util.UUID;

public class HavenEconomyTransactionEvent extends HavenEvent {
    public enum Type { WITHDRAW, DEPOSIT }

    private final UUID playerUuid;
    private final double amount;
    private final Type type;

    public HavenEconomyTransactionEvent(UUID playerUuid, double amount, Type type) {
        this.playerUuid = playerUuid;
        this.amount = amount;
        this.type = type;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public double getAmount() { return amount; }
    public Type getType() { return type; }
}
