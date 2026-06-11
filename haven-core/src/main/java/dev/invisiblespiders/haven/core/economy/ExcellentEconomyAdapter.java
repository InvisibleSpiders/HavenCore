package dev.invisiblespiders.haven.core.economy;

import dev.invisiblespiders.haven.api.model.EconomyAdapter;
import dev.invisiblespiders.haven.core.hook.ExcellentEconomyHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

import java.util.UUID;

public class ExcellentEconomyAdapter implements EconomyAdapter {

    private final ExcellentEconomyHook hook;
    private final String currencyId;

    public ExcellentEconomyAdapter(ExcellentEconomyHook hook, String currencyId) {
        this.hook = hook;
        this.currencyId = currencyId;
    }

    @Override
    public boolean isAvailable() { return hook.hasApiProvider(); }

    private ExcellentEconomyAPI api() { return hook.getApi(); }

    @Override
    public double getBalance(UUID uuid) {
        ExcellentEconomyAPI api = api();
        if (api == null) return 0;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return 0;
        return api.getBalance(player, currencyId);
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        ExcellentEconomyAPI api = api();
        if (api == null) return false;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        if (!has(uuid, amount)) return false;
        return api.withdraw(player, currencyId, amount);
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        ExcellentEconomyAPI api = api();
        if (api == null) return false;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        return api.deposit(player, currencyId, amount);
    }

    @Override
    public String format(double amount) {
        return String.format("%.2f %s", amount, currencyId);
    }
}
