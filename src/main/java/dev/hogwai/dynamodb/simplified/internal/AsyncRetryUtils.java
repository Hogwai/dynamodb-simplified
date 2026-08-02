package dev.hogwai.dynamodb.simplified.internal;

import java.util.concurrent.*;

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
        return delay(millis, RETRY_SCHEDULER);
    }

    /**
     * Schedules a non-cancellable retry delay on the supplied scheduler.
     *
     * @param millis    delay in milliseconds
     * @param scheduler scheduler used to complete the future
     * @return a future that completes after the delay
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    static CompletableFuture<Void> delay(long millis, ScheduledExecutorService scheduler) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable task = () -> future.complete(null);
        try {
            scheduler.schedule(task, millis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            future.completeExceptionally(exception);
            return future;
        }
        return future;
    }

    private AsyncRetryUtils() {
    }
}
