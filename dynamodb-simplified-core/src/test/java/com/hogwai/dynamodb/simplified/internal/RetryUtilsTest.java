package com.hogwai.dynamodb.simplified.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetryUtils")
class RetryUtilsTest {

    @Test
    @DisplayName("sleepWithBackoff(int) delegates to two-arg overload and returns false on success")
    void sleepWithBackoff_withAttempt_delegatesAndReturnsFalse() {
        // Attempt 0 with default base (100ms) should wait ~100-200ms, then return false
        boolean interrupted = RetryUtils.sleepWithBackoff(0);
        assertFalse(interrupted);
    }

    @Test
    @DisplayName("sleepWithBackoff returns true when thread is interrupted")
    void sleepWithBackoff_whenInterrupted_returnsTrue() {
        // Interrupt the current thread before calling sleepWithBackoff
        Thread.currentThread().interrupt();
        try {
            boolean interrupted = RetryUtils.sleepWithBackoff(0);
            assertTrue(interrupted, "Should return true when interrupted");
            assertTrue(Thread.currentThread().isInterrupted(), "Should preserve interrupt flag");
        } finally {
            // Clean up the interrupt flag for other tests
            Thread.interrupted();
        }
    }
}
