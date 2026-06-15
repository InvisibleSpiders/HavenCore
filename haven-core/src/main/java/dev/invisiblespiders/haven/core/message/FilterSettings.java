package dev.invisiblespiders.haven.core.message;

import java.util.List;

public record FilterSettings(int maxLength, List<String> blockedPatterns) {
    public static FilterSettings defaults() {
        return new FilterSettings(100, List.of());
    }
}
