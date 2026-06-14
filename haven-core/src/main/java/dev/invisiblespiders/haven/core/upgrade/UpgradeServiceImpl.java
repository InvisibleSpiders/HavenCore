package dev.invisiblespiders.haven.core.upgrade;

import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeEffect;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradeProvider;
import dev.invisiblespiders.haven.api.upgrade.UpgradePurchaseResult;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirementResult;
import dev.invisiblespiders.haven.api.upgrade.UpgradeScope;
import dev.invisiblespiders.haven.api.upgrade.UpgradeViewRequest;
import dev.invisiblespiders.haven.api.upgrade.UpgradeVisibility;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class UpgradeServiceImpl implements HavenUpgradeService {

    private final UpgradeRepository repository;
    private final Map<String, UpgradeProvider> providers = new LinkedHashMap<>();
    private final Map<String, UpgradeDefinition> definitions = new LinkedHashMap<>();

    public UpgradeServiceImpl(UpgradeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public synchronized void registerProvider(UpgradeProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String providerId = Objects.requireNonNull(provider.id(), "provider.id");
        if (providers.containsKey(providerId)) {
            throw new IllegalArgumentException("Duplicate upgrade provider id: " + providerId);
        }

        List<UpgradeDefinition> providerDefinitions = List.copyOf(
                Objects.requireNonNull(provider.definitions(), "provider.definitions"));
        for (UpgradeDefinition definition : providerDefinitions) {
            String upgradeId = Objects.requireNonNull(definition.id(), "definition.id");
            if (definitions.containsKey(upgradeId)) {
                throw new IllegalArgumentException("Duplicate upgrade id: " + upgradeId);
            }
            if (providerDefinitions.stream().filter(candidate -> candidate.id().equals(upgradeId)).count() > 1) {
                throw new IllegalArgumentException("Duplicate upgrade id: " + upgradeId);
            }
        }

        providers.put(providerId, provider);
        for (UpgradeDefinition definition : providerDefinitions) {
            definitions.put(definition.id(), definition);
        }
    }

    @Override
    public synchronized void unregisterProvider(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        providers.remove(providerId);
        definitions.entrySet().removeIf(entry -> entry.getValue().providerId().equals(providerId));
    }

    @Override
    public synchronized List<UpgradeProvider> providers() {
        return List.copyOf(providers.values());
    }

    @Override
    public synchronized List<UpgradeDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    @Override
    public synchronized Optional<UpgradeDefinition> findDefinition(String upgradeId) {
        Objects.requireNonNull(upgradeId, "upgradeId");
        return Optional.ofNullable(definitions.get(upgradeId));
    }

    @Override
    public int currentLevel(UUID playerId, String upgradeId) {
        return repository.currentLevel(playerId, upgradeId);
    }

    @Override
    public UpgradePurchaseResult purchase(Player purchaser, String upgradeId) {
        return purchase(purchaser, upgradeId, UpgradeScope.PLAYER);
    }

    @Override
    public UpgradePurchaseResult purchase(Player purchaser, String upgradeId, UpgradeScope scope) {
        Objects.requireNonNull(purchaser, "purchaser");
        Objects.requireNonNull(upgradeId, "upgradeId");
        Objects.requireNonNull(scope, "scope");

        UpgradeDefinition definition = findDefinition(upgradeId).orElse(null);
        if (definition == null) {
            return failure("unknown-upgrade");
        }

        UUID targetPlayerId = purchaser.getUniqueId();
        int nextLevel = repository.currentLevel(targetPlayerId, upgradeId) + 1;
        UpgradeLevel level = findLevel(definition, nextLevel).orElse(null);
        if (level == null) {
            return failure("max-level");
        }

        if (definition.visibility() == UpgradeVisibility.HIDDEN) {
            return failure("hidden-upgrade");
        }
        if (definition.visibility() == UpgradeVisibility.LOCKED) {
            return failure("locked-upgrade");
        }
        if (hasPermissionGate(definition) && !purchaser.hasPermission(definition.permission())) {
            return failure("no-permission");
        }

        UpgradeContext context = new UpgradeContext(
                purchaser, targetPlayerId, upgradeId, nextLevel, scope, level.metadata());
        UpgradePurchaseResult requirementValidation = validateRequirements(level.requirements(), context);
        if (!requirementValidation.succeeded()) {
            return requirementValidation;
        }

        UpgradePurchaseResult effectValidation = validateEffects(level.effects(), context);
        if (!effectValidation.succeeded()) {
            return effectValidation;
        }

        List<UpgradeRequirement> consumedRequirements = new ArrayList<>();
        UpgradePurchaseResult consumeResult = consumeRequirements(level.requirements(), context, consumedRequirements);
        if (!consumeResult.succeeded()) {
            refund(consumedRequirements, context);
            return consumeResult;
        }

        List<UpgradeEffect> appliedEffects = new ArrayList<>();
        UpgradePurchaseResult effectResult = applyEffects(level.effects(), context, appliedEffects);
        if (!effectResult.succeeded()) {
            rollback(appliedEffects, context);
            refund(consumedRequirements, context);
            return effectResult;
        }

        try {
            repository.recordPurchase(definition.providerId(), upgradeId, targetPlayerId, scope.name(), nextLevel,
                    purchaser.getUniqueId(), "purchase");
        } catch (RuntimeException e) {
            rollback(appliedEffects, context);
            refund(consumedRequirements, context);
            return UpgradePurchaseResult.failure("record-failed", e.getMessage());
        }
        return UpgradePurchaseResult.success("Upgrade purchased.");
    }

    @Override
    public UpgradePurchaseResult grant(UUID targetPlayerId, String upgradeId, int level, String source) {
        Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        Objects.requireNonNull(upgradeId, "upgradeId");
        Objects.requireNonNull(source, "source");

        UpgradeDefinition definition = findDefinition(upgradeId).orElse(null);
        if (definition == null) {
            return failure("unknown-upgrade");
        }
        repository.recordPurchase(definition.providerId(), upgradeId, targetPlayerId, definition.scope().name(),
                level, targetPlayerId, source);
        return UpgradePurchaseResult.success("Upgrade granted.");
    }

    @Override
    public UpgradePurchaseResult revoke(UUID targetPlayerId, String upgradeId, String source) {
        Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        Objects.requireNonNull(upgradeId, "upgradeId");
        Objects.requireNonNull(source, "source");

        if (findDefinition(upgradeId).isEmpty()) {
            return failure("unknown-upgrade");
        }
        repository.revoke(targetPlayerId, upgradeId);
        return UpgradePurchaseResult.success("Upgrade revoked.");
    }

    @Override
    public void openDialog(Player player, UpgradeViewRequest request) {
        // Task 6 owns the upgrade UI.
    }

    private Optional<UpgradeLevel> findLevel(UpgradeDefinition definition, int nextLevel) {
        return definition.levels().stream()
                .filter(level -> level.level() == nextLevel)
                .findFirst();
    }

    private boolean hasPermissionGate(UpgradeDefinition definition) {
        return definition.permission() != null && !definition.permission().isBlank();
    }

    private UpgradePurchaseResult validateRequirements(
            List<UpgradeRequirement> requirements, UpgradeContext context) {
        for (UpgradeRequirement requirement : requirements) {
            UpgradeRequirementResult result = requirement.validate(context);
            if (!result.satisfied()) {
                return UpgradePurchaseResult.failure("requirement-not-met", result.message());
            }
        }
        return UpgradePurchaseResult.success("");
    }

    private UpgradePurchaseResult validateEffects(List<UpgradeEffect> effects, UpgradeContext context) {
        for (UpgradeEffect effect : effects) {
            UpgradeRequirementResult result = effect.validate(context);
            if (!result.satisfied()) {
                return UpgradePurchaseResult.failure("effect-invalid", result.message());
            }
        }
        return UpgradePurchaseResult.success("");
    }

    private UpgradePurchaseResult consumeRequirements(
            List<UpgradeRequirement> requirements, UpgradeContext context, List<UpgradeRequirement> consumed) {
        for (UpgradeRequirement requirement : requirements) {
            try {
                requirement.consume(context);
                consumed.add(requirement);
            } catch (RuntimeException e) {
                return UpgradePurchaseResult.failure("requirement-consume-failed", e.getMessage());
            }
        }
        return UpgradePurchaseResult.success("");
    }

    private UpgradePurchaseResult applyEffects(
            List<UpgradeEffect> effects, UpgradeContext context, List<UpgradeEffect> applied) {
        for (UpgradeEffect effect : effects) {
            try {
                effect.apply(context);
                applied.add(effect);
            } catch (RuntimeException e) {
                return UpgradePurchaseResult.failure("effect-failed", e.getMessage());
            }
        }
        return UpgradePurchaseResult.success("");
    }

    private void rollback(List<UpgradeEffect> effects, UpgradeContext context) {
        List<UpgradeEffect> reversed = new ArrayList<>(effects);
        Collections.reverse(reversed);
        for (UpgradeEffect effect : reversed) {
            effect.rollback(context);
        }
    }

    private void refund(List<UpgradeRequirement> requirements, UpgradeContext context) {
        List<UpgradeRequirement> reversed = new ArrayList<>(requirements);
        Collections.reverse(reversed);
        for (UpgradeRequirement requirement : reversed) {
            requirement.refund(context);
        }
    }

    private UpgradePurchaseResult failure(String code) {
        return UpgradePurchaseResult.failure(code, code);
    }
}
