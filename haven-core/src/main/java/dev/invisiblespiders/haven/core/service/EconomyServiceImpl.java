package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenEconomyTransactionEvent;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.core.economy.ItemEconomyAdapter;
import dev.invisiblespiders.haven.core.economy.MoneyEconomyAdapter;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EconomyServiceImpl implements HavenEconomyService {

    private final MoneyEconomyAdapter money;
    private final ItemEconomyAdapter  item;
    private final HavenEventBus eventBus;

    public EconomyServiceImpl(MoneyEconomyAdapter money, ItemEconomyAdapter item, HavenEventBus eventBus) {
        this.money = money;
        this.item = item;
        this.eventBus = eventBus;
    }

    @Override public boolean isMoneyAvailable() { return money.isAvailable(); }
    @Override public boolean isItemAvailable()  { return item.isAvailable(); }

    @Override
    public double getBalance(UUID uuid) { return money.getBalance(uuid); }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        boolean ok = money.withdraw(uuid, amount);
        if (ok) eventBus.publish(new HavenEconomyTransactionEvent(uuid, amount, HavenEconomyTransactionEvent.Type.WITHDRAW));
        return ok;
    }

    @Override
    public void deposit(UUID uuid, double amount) {
        money.deposit(uuid, amount);
        eventBus.publish(new HavenEconomyTransactionEvent(uuid, amount, HavenEconomyTransactionEvent.Type.DEPOSIT));
    }

    @Override
    public boolean has(UUID uuid, double amount) { return money.has(uuid, amount); }

    @Override
    public String format(double amount) { return money.format(amount); }

    @Override
    public int getItemBalance(Player player)         { return item.countItems(player); }

    @Override
    public int withdrawItems(Player player, int count) { return item.removeItems(player, count); }

    @Override
    public void depositItems(Player player, int count)  { item.giveItems(player, count); }
}
