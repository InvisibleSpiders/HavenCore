package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.diagnostic.DiagnosticResult;
import dev.invisiblespiders.haven.api.diagnostic.HavenDiagnosticCheck;
import dev.invisiblespiders.haven.api.service.HavenDiagnosticService;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DiagnosticServiceImpl implements HavenDiagnosticService {

    private final Map<String, HavenDiagnosticCheck> checks = new ConcurrentHashMap<>();

    @Override
    public void register(String ownerId, HavenDiagnosticCheck check) {
        String owner = normalize(ownerId, "ownerId");
        Objects.requireNonNull(check, "check");
        String checkId = normalize(check.id(), "check.id");
        checks.put(key(owner, checkId), check);
    }

    @Override
    public void unregister(String ownerId, String checkId) {
        checks.remove(key(normalize(ownerId, "ownerId"), normalize(checkId, "checkId")));
    }

    @Override
    public void unregisterAll(String ownerId) {
        String ownerPrefix = normalize(ownerId, "ownerId") + ".";
        checks.keySet().removeIf(id -> id.startsWith(ownerPrefix));
    }

    @Override
    public List<DiagnosticResult> runAll() {
        return checks.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> run(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(DiagnosticResult::id))
            .toList();
    }

    private DiagnosticResult run(String key, HavenDiagnosticCheck check) {
        try {
            DiagnosticResult result = check.run();
            if (result == null) {
                return DiagnosticResult.fail(key, "Diagnostic check returned no result.");
            }
            return result;
        } catch (RuntimeException e) {
            return DiagnosticResult.fail(key, "Diagnostic check failed: " + e.getMessage());
        }
    }

    private String key(String ownerId, String checkId) {
        return ownerId + "." + checkId;
    }

    private static String normalize(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
