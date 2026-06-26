package com.hogwai.dynamodb.simplified.internal;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared utility for retry-related operations.
 * <p>
 * Consolidates duplicated backoff and retry logic across sync builders.
 */
public final class RetryUtils {

    /** Default base backoff in milliseconds for exponential retry. */
    public static final long DEFAULT_BASE_BACKOFF_MS = 100;

    /** Default maximum number of retry attempts. */
    public static final int DEFAULT_MAX_RETRIES = 3;

    private RetryUtils() {
    }

    /**
     * Sleeps with exponential backoff plus jitter.
     * <p>
     * Formula: {@code baseBackoffMs * (1 << attempt) + random(baseBackoffMs)}
     *
     * @param attempt       the current retry attempt (0-based)
     * @param baseBackoffMs the base backoff in milliseconds
     * @return {@code true} if the sleep was interrupted (caller should abort retry),
     *         {@code false} if the sleep completed normally
     */
    public static boolean sleepWithBackoff(int attempt, long baseBackoffMs) {
        long backoff = baseBackoffMs * (1L << attempt);
        backoff += ThreadLocalRandom.current().nextLong(baseBackoffMs);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return true;
        }
        return false;
    }

    /**
     * Sleeps with exponential backoff plus jitter using the default base of 100ms.
     *
     * @param attempt the current retry attempt (0-based)
     * @return {@code true} if interrupted, {@code false} otherwise
     * @see #sleepWithBackoff(int, long)
     */
    public static boolean sleepWithBackoff(int attempt) {
        return sleepWithBackoff(attempt, DEFAULT_BASE_BACKOFF_MS);
    }
}
