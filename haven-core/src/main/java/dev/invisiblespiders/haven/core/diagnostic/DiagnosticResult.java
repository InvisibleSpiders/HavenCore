package dev.invisiblespiders.haven.core.diagnostic;

public record DiagnosticResult(DiagnosticSeverity severity, String id, String message) {}
