package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.diagnostic.DiagnosticResult;
import dev.invisiblespiders.haven.api.diagnostic.HavenDiagnosticCheck;

import java.util.List;

public interface HavenDiagnosticService {

    void register(String ownerId, HavenDiagnosticCheck check);

    void unregister(String ownerId, String checkId);

    void unregisterAll(String ownerId);

    List<DiagnosticResult> runAll();
}
