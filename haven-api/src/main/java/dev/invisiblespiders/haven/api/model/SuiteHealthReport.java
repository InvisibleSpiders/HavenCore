package dev.invisiblespiders.haven.api.model;

public record SuiteHealthReport(SuiteHealthSeverity severity, String check, String message) {}
