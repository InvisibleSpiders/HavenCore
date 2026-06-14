package dev.invisiblespiders.haven.api.reward;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RewardApiModelTest {
    @Test
    void rewardRecordExposesProviderPayloadAndExpiry() {
        UUID playerId = UUID.randomUUID();
        RewardRecord record = new RewardRecord(
                17L,
                "havenvault",
                "server-bank-item",
                playerId,
                "Server Bank Item Bundle",
                Map.of("command", "give <player> diamond 1"),
                RewardStatus.PENDING,
                Instant.parse("2026-06-14T12:00:00Z"),
                Instant.parse("2026-06-21T12:00:00Z"),
                null
        );

        assertEquals(17L, record.id());
        assertEquals("havenvault", record.providerId());
        assertEquals(playerId, record.playerId());
        assertEquals(RewardStatus.PENDING, record.status());
        assertTrue(record.payload().containsKey("command"));
    }
}
