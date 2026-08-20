package dev.hogwai.dynamodb.simplified.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utility for non-blocking async retry delays.
 * <p>
 * Uses the JDK delayed executor so the library does not own a permanent
 * scheduler thread.
 */
public final class AsyncRetryUtils {

    /**
     * Returns a {@link CompletableFuture} that completes after the specified delay.
     *
     * @param millis delay in milliseconds
     * @return a future that completes after the delay
     */
    public static CompletableFuture<Void> delay(long millis) {
        return CompletableFuture.runAsync(
                () -> {
                },
                CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS));
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
