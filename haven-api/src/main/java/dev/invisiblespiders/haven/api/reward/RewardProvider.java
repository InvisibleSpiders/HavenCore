package dev.invisiblespiders.haven.api.reward;

import java.util.List;
import java.util.Optional;

public interface RewardProvider {

    String id();

    String displayName();

    List<RewardDefinition> rewards();

    Optional<RewardHandler> handler(String rewardType);
}
