package dev.invisiblespiders.haven.api.chat;

public enum ChatRecipientType {
    /** Broadcast to all online players. */
    ALL,
    /** Broadcast to players with a specific permission node. */
    PERMISSION,
    /** Broadcast to players within radiusBlocks of the sender (same world). */
    RADIUS,
    /**
     * Broadcast to players in the same HavenClaims claim as the sender.
     * Degrades to ALL with a log warning until HavenClaimsService is registered.
     */
    CLAIM
}
