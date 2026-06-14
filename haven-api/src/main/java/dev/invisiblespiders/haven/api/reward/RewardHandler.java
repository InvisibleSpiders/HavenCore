package dev.invisiblespiders.haven.api.reward;

import org.bukkit.entity.Player;

public interface RewardHandler {

    String rewardType();

    RewardClaimResult claim(Player player, RewardRecord reward);
}
