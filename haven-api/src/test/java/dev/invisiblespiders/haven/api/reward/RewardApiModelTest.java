package dev.invisiblespiders.haven.api.reward;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
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

    @Test
    void rewardRecordCopiesPayloadAndRejectsMutation() {
        Map<String, String> payload = new HashMap<>();
        payload.put("command", "give <player> diamond 1");

        RewardRecord record = new RewardRecord(
                17L,
                "havenvault",
                "server-bank-item",
                UUID.randomUUID(),
                "Server Bank Item Bundle",
                payload,
                RewardStatus.PENDING,
                Instant.parse("2026-06-14T12:00:00Z"),
                null,
                null
        );
        payload.put("command", "give <player> dirt 1");

        assertEquals("give <player> diamond 1", record.payload().get("command"));
        assertThrows(UnsupportedOperationException.class, () -> record.payload().put("other", "value"));
    }

    @Test
    void rewardDefinitionCopiesMetadataAndRejectsMutation() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("rarity", "rare");

        RewardDefinition definition = new RewardDefinition(
                "havenvault",
                "server-bank-item",
                "Server Bank Item Bundle",
                metadata
        );
        metadata.put("rarity", "common");

        assertEquals("rare", definition.metadata().get("rarity"));
        assertThrows(UnsupportedOperationException.class, () -> definition.metadata().clear());
    }

    @Test
    void requiredRewardFieldsRejectNulls() {
        assertThrows(NullPointerException.class, () -> new RewardDefinition(
                null,
                "server-bank-item",
                "Server Bank Item Bundle",
                Map.of()
        ));
        assertThrows(NullPointerException.class, () -> new RewardRecord(
                17L,
                "havenvault",
                "server-bank-item",
                UUID.randomUUID(),
                "Server Bank Item Bundle",
                null,
                RewardStatus.PENDING,
                Instant.parse("2026-06-14T12:00:00Z"),
                null,
                null
        ));
        assertThrows(NullPointerException.class, () -> RewardClaimResult.failure(null, "No reward found."));
    }
}
