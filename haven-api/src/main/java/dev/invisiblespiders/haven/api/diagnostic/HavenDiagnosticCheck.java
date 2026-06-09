package dev.invisiblespiders.haven.api.diagnostic;

public interface HavenDiagnosticCheck {

    /**
     * Short check id scoped to the registering plugin, for example {@code database}.
     */
    String id();

    DiagnosticResult run();
}
