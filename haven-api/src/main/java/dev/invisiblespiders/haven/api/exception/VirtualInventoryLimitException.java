package dev.invisiblespiders.haven.api.exception;

/**
 * Raised when a player cannot create another virtual inventory because the
 * configured per-player storage limit has been reached.
 */
public class VirtualInventoryLimitException extends HavenStorageServiceException {

    public VirtualInventoryLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
