package dev.invisiblespiders.haven.api.sleep;

import dev.invisiblespiders.haven.api.event.HavenSleepSkipCompleteEvent;
import dev.invisiblespiders.haven.api.event.HavenSleepSkipStartEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SleepApiModelTest {

    @Test
    void skipStartEventExposesAllFields() {
        HavenSleepSkipStartEvent event = new HavenSleepSkipStartEvent(null, 2, 5);
        assertNull(event.getWorld());
        assertEquals(2, event.getSleeping());
        assertEquals(5, event.getActive());
        assertTrue(event.getTimestamp() > 0);
    }

    @Test
    void skipCompleteEventExposesWorld() {
        HavenSleepSkipCompleteEvent event = new HavenSleepSkipCompleteEvent(null);
        assertNull(event.getWorld());
        assertTrue(event.getTimestamp() > 0);
    }
}
