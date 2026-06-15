package dev.invisiblespiders.haven.core.reward;

import dev.invisiblespiders.haven.api.reward.HavenRewardService;
import dev.invisiblespiders.haven.api.reward.RewardClaimResult;
import dev.invisiblespiders.haven.api.reward.RewardHandler;
import dev.invisiblespiders.haven.api.reward.RewardProvider;
import dev.invisiblespiders.haven.api.reward.RewardRecord;
import dev.invisiblespiders.haven.api.reward.RewardStatus;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RewardServiceImpl implements HavenRewardService {

    private static final String SYSTEM_SOURCE = "system";

    private final RewardRepository repository;
    private final ConcurrentMap<String, RewardProvider> providers = new ConcurrentHashMap<>();

    public RewardServiceImpl(RewardRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void registerProvider(RewardProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String providerId = Objects.requireNonNull(provider.id(), "provider id");
        RewardProvider existing = providers.putIfAbsent(providerId, provider);
        if (existing != null) {
            throw new IllegalArgumentException("Reward provider already registered: " + providerId);
        }
    }

    @Override
    public void unregisterProvider(String providerId) {
        providers.remove(Objects.requireNonNull(providerId, "providerId"));
    }

    @Override
    public RewardRecord enqueue(UUID playerId, String providerId, String rewardType, String displayText,
                                Map<String, String> payload, Instant expiresAt) {
        return repository.enqueue(playerId, providerId, rewardType, displayText, payload, expiresAt, SYSTEM_SOURCE);
    }

    @Override
    public List<RewardRecord> pending(UUID playerId) {
        return repository.pending(playerId, Instant.now());
    }

    @Override
    public synchronized RewardClaimResult claim(Player player, long rewardId) {
        Objects.requireNonNull(player, "player");
        Instant now = Instant.now();
        Optional<RewardRecord> found = repository.find(rewardId);
        if (found.isEmpty()) {
            return RewardClaimResult.failure("missing-reward", "Reward was not found.");
        }

        RewardRecord reward = found.get();
        if (reward.status() != RewardStatus.PENDING) {
            return RewardClaimResult.failure("reward-unavailable", "Reward is no longer available.");
        }
        if (!reward.playerId().equals(player.getUniqueId())) {
            return RewardClaimResult.failure("reward-unavailable", "Reward is no longer available.");
        }
        if (isExpired(reward, now)) {
            repository.expire(now);
            return RewardClaimResult.failure("reward-expired", "Reward has expired.");
        }

        RewardProvider provider = providers.get(reward.providerId());
        if (provider == null) {
            return RewardClaimResult.failure("provider-unavailable", "Reward provider is unavailable.");
        }

        Optional<RewardHandler> handler = provider.handler(reward.rewardType());
        if (handler.isEmpty()) {
            return RewardClaimResult.failure("reward-unavailable", "Reward is no longer available.");
        }

        RewardClaimResult result = handler.orElseThrow().claim(player, reward);
        if (!result.succeeded()) {
            return result;
        }

        if (repository.claim(rewardId, now).isEmpty()) {
            return RewardClaimResult.failure("reward-unavailable", "Reward is no longer available.");
        }
        return result;
    }

    @Override
    public synchronized int expireRewards(Instant now) {
        return repository.expire(now);
    }

    @Override
    public synchronized Optional<RewardRecord> revoke(long rewardId, String source) {
        return repository.revoke(rewardId, source);
    }

    private static boolean isExpired(RewardRecord reward, Instant now) {
        return reward.expiresAt() != null && !reward.expiresAt().isAfter(now);
    }
}
