package dev.invisiblespiders.haven.api.service;

public record DataSourceHealth(
    boolean initialized,
    boolean open,
    int activeConnections,
    int idleConnections,
    int totalConnections,
    int threadsAwaitingConnection
) {

    public static DataSourceHealth uninitialized() {
        return new DataSourceHealth(false, false, 0, 0, 0, 0);
    }

    public static DataSourceHealth closed() {
        return new DataSourceHealth(true, false, 0, 0, 0, 0);
    }
}
