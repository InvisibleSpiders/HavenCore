package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenCodexDiscoveryEvent;
import dev.invisiblespiders.haven.api.event.HavenCodexMilestoneEvent;
import dev.invisiblespiders.haven.api.model.CodexCategory;
import dev.invisiblespiders.haven.api.model.CodexEntry;
import dev.invisiblespiders.haven.api.model.PlayerCodex;
import dev.invisiblespiders.haven.api.service.HavenCodexService;
import dev.invisiblespiders.haven.api.service.HavenEventBus;
import dev.invisiblespiders.haven.core.repository.CodexRepository;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CodexServiceImpl implements HavenCodexService {

    private final CodexRepository repo;
    private final HavenEventBus eventBus;
    private final Executor asyncExecutor;
    private final Logger logger;
    // categoryId -> list of milestone thresholds (sorted)
    private final Map<String, List<Integer>> milestones;

    private final Map<String, CodexEntry> entries = new ConcurrentHashMap<>();

    public CodexServiceImpl(CodexRepository repo, HavenEventBus eventBus,
                            Executor asyncExecutor, FileConfiguration codexConfig, Logger logger) {
        this.repo = repo;
        this.eventBus = eventBus;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
        this.milestones = loadMilestones(codexConfig);
    }

    private Map<String, List<Integer>> loadMilestones(FileConfiguration cfg) {
        Map<String, List<Integer>> map = new HashMap<>();
        var section = cfg.getConfigurationSection("milestones");
        if (section == null) return map;
        for (String catId : section.getKeys(false)) {
            List<Integer> thresholds = section.getIntegerList(catId);
            Collections.sort(thresholds);
            map.put(catId, thresholds);
        }
        return map;
    }

    @Override
    public void registerEntry(CodexEntry entry) {
        entries.put(entry.key(), entry);
    }

    @Override
    public Optional<CodexEntry> getEntry(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    @Override
    public Collection<CodexEntry> getEntries(CodexCategory category) {
        return entries.values().stream()
            .filter(e -> e.category().equals(category))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<CodexEntry> getAllEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    @Override
    public CompletableFuture<PlayerCodex> getCodex(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try { return repo.load(playerUuid); }
            catch (SQLException e) { throw new RuntimeException(e); }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Boolean> recordDiscovery(UUID playerUuid, String entryKey) {
        CodexEntry entry = entries.get(entryKey);
        if (entry == null) return CompletableFuture.completedFuture(false);

        return CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                boolean inserted = repo.insert(playerUuid, entryKey, now);
                if (!inserted) return false;

                eventBus.publish(new HavenCodexDiscoveryEvent(playerUuid, entry));

                // Check milestones for this category
                List<Integer> thresholds = milestones.getOrDefault(entry.category().getId(), List.of());
                if (!thresholds.isEmpty()) {
                    int count = repo.countByCategory(playerUuid, entry.category().getId());
                    if (thresholds.contains(count)) {
                        eventBus.publish(new HavenCodexMilestoneEvent(playerUuid, entry.category(), count));
                    }
                }
                return true;
            } catch (SQLException e) {
                logger.warning("CodexService recordDiscovery failed: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Integer> getDiscoveryCount(UUID playerUuid, CodexCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            try { return repo.countByCategory(playerUuid, category.getId()); }
            catch (SQLException e) { throw new RuntimeException(e); }
        }, asyncExecutor);
    }
}
