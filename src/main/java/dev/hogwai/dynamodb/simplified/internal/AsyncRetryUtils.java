package dev.hogwai.dynamodb.simplified.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utility for non-blocking async retry delays.
 * <p>
 * Uses a dedicated daemon scheduled executor to avoid blocking
 * the common fork-join pool or caller threads.
 */
public final class AsyncRetryUtils {

    private static final ScheduledExecutorService RETRY_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dynamodb-simplified-retry");
                t.setDaemon(true);
                return t;
            });

    /**
     * Returns a {@link CompletableFuture} that completes after the specified delay.
     *
     * @param millis delay in milliseconds
     * @return a future that completes after the delay
     */
    public static CompletableFuture<Void> delay(long millis) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        RETRY_SCHEDULER.schedule(() -> future.complete(null), millis, TimeUnit.MILLISECONDS);
        return future;
    }

    private AsyncRetryUtils() {
    }
}
