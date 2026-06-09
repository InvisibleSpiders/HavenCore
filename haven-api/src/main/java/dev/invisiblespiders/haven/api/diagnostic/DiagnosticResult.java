package dev.invisiblespiders.haven.api.diagnostic;

public record DiagnosticResult(DiagnosticSeverity severity, String id, String message) {

    public static DiagnosticResult pass(String id, String message) {
        return new DiagnosticResult(DiagnosticSeverity.PASS, id, message);
    }

    public static DiagnosticResult warn(String id, String message) {
        return new DiagnosticResult(DiagnosticSeverity.WARN, id, message);
    }

    public static DiagnosticResult fail(String id, String message) {
        return new DiagnosticResult(DiagnosticSeverity.FAIL, id, message);
    }
}
