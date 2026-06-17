package dev.invisiblespiders.haven.core.upgrade;

import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirementResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class ItemRequirement implements UpgradeRequirement {

    private final Material material;
    private final int amount;

    public ItemRequirement(Material material, int amount) {
        this.material = Objects.requireNonNull(material, "material");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.amount = amount;
    }

    @Override
    public String type() {
        return "item";
    }

    @Override
    public UpgradeRequirementResult validate(UpgradeContext context) {
        Player player = requireOnlinePlayer(context);
        return count(player) >= amount
                ? UpgradeRequirementResult.success()
                : UpgradeRequirementResult.failure("insufficient-items", "Insufficient items.");
    }

    @Override
    public void consume(UpgradeContext context) {
        Player player = requireOnlinePlayer(context);
        if (count(player) < amount) {
            throw new IllegalStateException("item withdrawal failed");
        }
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int remove = Math.min(remaining, stack.getAmount());
            int newAmount = stack.getAmount() - remove;
            if (newAmount > 0) {
                stack.setAmount(newAmount);
                contents[i] = stack;
            } else {
                contents[i] = null;
            }
            remaining -= remove;
        }
        player.getInventory().setContents(contents);
    }

    @Override
    public void refund(UpgradeContext context) {
        requireOnlinePlayer(context).getInventory().addItem(new ItemStack(material, amount));
    }

    private int count(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private Player requireOnlinePlayer(UpgradeContext context) {
        if (context.purchaser() == null) {
            throw new IllegalStateException("online player is required");
        }
        return context.purchaser();
    }

    @Override
    public Component describe() {
        return Component.text(amount + "x " + formatMaterialName(material), NamedTextColor.YELLOW);
    }

    private String formatMaterialName(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
