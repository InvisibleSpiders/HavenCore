package dev.invisiblespiders.haven.core.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

class HavenAsyncExecutorsTest {

    @Test
    void createBuildsFixedSizeNamedExecutor() throws Exception {
        ExecutorService executor = HavenAsyncExecutors.create(2);
        try {
            assertInstanceOf(ThreadPoolExecutor.class, executor);
            ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
            assertEquals(2, pool.getCorePoolSize());
            assertEquals(2, pool.getMaximumPoolSize());

            String threadName = executor.submit(() -> Thread.currentThread().getName()).get();
            assertTrue(threadName.startsWith("HavenCore-Async-"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void createRejectsInvalidThreadCount() {
        assertThrows(IllegalArgumentException.class, () -> HavenAsyncExecutors.create(0));
    }
}
