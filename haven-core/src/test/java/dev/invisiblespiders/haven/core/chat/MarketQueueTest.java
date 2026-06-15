package dev.invisiblespiders.haven.core.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketQueueTest {

    @Test
    void emptyByDefault() {
        MarketQueue queue = new MarketQueue();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isEqualTo(0);
    }

    @Test
    void enqueueAndPoll() {
        MarketQueue queue = new MarketQueue();
        var req = new AdvertisementRequest(UUID.randomUUID(), "Alice", "AliceShop", "Selling diamonds", System.currentTimeMillis());
        queue.enqueue(req);
        assertThat(queue.isEmpty()).isFalse();
        assertThat(queue.size()).isEqualTo(1);
        var polled = queue.poll();
        assertThat(polled).isEqualTo(req);
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void fifoOrdering() {
        MarketQueue queue = new MarketQueue();
        var first = new AdvertisementRequest(UUID.randomUUID(), "Alice", "AliceShop", "First", 1L);
        var second = new AdvertisementRequest(UUID.randomUUID(), "Bob", "BobShop", "Second", 2L);
        queue.enqueue(first);
        queue.enqueue(second);
        assertThat(queue.poll()).isEqualTo(first);
        assertThat(queue.poll()).isEqualTo(second);
    }

    @Test
    void pollReturnsNullWhenEmpty() {
        MarketQueue queue = new MarketQueue();
        assertThat(queue.poll()).isNull();
    }
}
