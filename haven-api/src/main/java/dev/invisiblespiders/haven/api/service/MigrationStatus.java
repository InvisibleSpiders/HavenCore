package dev.invisiblespiders.haven.api.service;

public record MigrationStatus(String pluginId, int version, String script, long appliedAt) {}
