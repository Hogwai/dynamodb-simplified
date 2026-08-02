package dev.hogwai.dynamodb.simplified.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncRetryUtils")
class AsyncRetryUtilsTest {

    @Mock
    private ScheduledExecutorService scheduler;

    @Test
    @DisplayName("delay(millis, scheduler) completes after runnable is executed by the scheduler")
    void delay_withScheduler_completesAfterRunnableExecutes() {
        // Arrange
        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> mockFuture = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(mockFuture)
                .when(scheduler).schedule(captor.capture(), eq(10L), eq(TimeUnit.MILLISECONDS));

        // Act
        CompletableFuture<Void> future = AsyncRetryUtils.delay(10, scheduler);

        // Assert: future must NOT be completed before the runnable fires
        assertFalse(future.isDone(), "Future should not be done before scheduler runs the task");

        // Simulate the scheduler executing the delayed runnable
        Runnable scheduled = captor.getValue();
        scheduled.run();

        // Now the future should be completed
        assertTrue(future.isDone(), "Future should be done after runnable execution");
        verify(scheduler).schedule(any(Runnable.class), eq(10L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("delay(millis, scheduler) returns exceptionally completed future when scheduler rejects")
    void delay_withScheduler_rejectedExceptionCompletesExceptionally() {
        // Arrange
        RejectedExecutionException rejected = new RejectedExecutionException("scheduler shutdown");
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenThrow(rejected);

        // Act: must not throw synchronously
        CompletableFuture<Void> future = AsyncRetryUtils.delay(10, scheduler);

        // Assert: a future completed exceptionally is done
        assertTrue(future.isDone(), "Future should be done after rejection");
        assertTrue(future.isCompletedExceptionally(),
                "Future should be exceptionally completed after rejection");
        CompletionException exception =
                assertThrows(java.util.concurrent.CompletionException.class, future::join);
        assertEquals(rejected, exception.getCause(), "Cause should be the RejectedExecutionException");
    }
}
