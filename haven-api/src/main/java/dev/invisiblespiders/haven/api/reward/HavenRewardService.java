package dev.invisiblespiders.haven.api.reward;

import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface HavenRewardService {

    void registerProvider(RewardProvider provider);

    void unregisterProvider(String providerId);

    RewardRecord enqueue(
            UUID playerId,
            String providerId,
            String rewardType,
            String displayText,
            Map<String, String> payload,
            Instant expiresAt
    );

    List<RewardRecord> pending(UUID playerId);

    RewardClaimResult claim(Player player, long rewardId);

    int expireRewards(Instant now);

    Optional<RewardRecord> revoke(long rewardId, String source);
}
