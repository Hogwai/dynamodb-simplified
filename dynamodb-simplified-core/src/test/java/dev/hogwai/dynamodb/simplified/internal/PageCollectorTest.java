package dev.hogwai.dynamodb.simplified.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("PageCollector")
class PageCollectorTest {

    @Test
    @DisplayName("collectPages propagates publisher error to the returned future")
    void collectPages_propagatesError() {
        RuntimeException expectedError = new RuntimeException("test error");

        SdkPublisher<Page<String>> publisher = subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onError(expectedError);
        };

        CompletableFuture<List<Page<String>>> future = PageCollector.collectPages(publisher);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertSame(expectedError, ex.getCause());
    }

    @Test
    @DisplayName("collectPages returns collected pages on success")
    void collectPages_returnsPages() throws Exception {
        Page<String> page1 = mock();
        Page<String> page2 = mock();

        SdkPublisher<Page<String>> publisher = subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onNext(page1);
            subscriber.onNext(page2);
            subscriber.onComplete();
        };

        CompletableFuture<List<Page<String>>> future = PageCollector.collectPages(publisher);

        List<Page<String>> result = future.get();
        assertEquals(2, result.size());
        assertSame(page1, result.get(0));
        assertSame(page2, result.get(1));
    }
}
