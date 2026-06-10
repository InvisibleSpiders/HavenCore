package dev.invisiblespiders.haven.core.async;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class HavenAsyncExecutors {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private HavenAsyncExecutors() {}

    public static ExecutorService create(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be at least 1");
        }
        return Executors.newFixedThreadPool(threads, new NamedThreadFactory());
    }

    public static void shutdown(ExecutorService executor, Logger logger) {
        if (executor == null) return;

        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                logger.warning("Forced HavenCore async executor shutdown with tasks still pending.");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warning("Interrupted while shutting down HavenCore async executor.");
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "HavenCore-Async-" + count.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
