package dev.hogwai.dynamodb.simplified.internal;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Utility for bridging Reactive Streams page publishers to CompletableFuture.
 */
public final class PageCollector {

    private static final int DEFAULT_PREFETCH_LIMIT = 10;

    private PageCollector() {
    }

    /**
     * Subscribes to a page publisher and collects all pages into a CompletableFuture list.
     *
     * @param <T>       the page item type
     * @param publisher the page publisher to subscribe to
     * @return a CompletableFuture that completes with the full list of collected pages
     */
    public static <T> CompletableFuture<List<Page<T>>> collectPages(SdkPublisher<Page<T>> publisher) {
        return collectPages(publisher, DEFAULT_PREFETCH_LIMIT);
    }

    /**
     * Subscribes to a page publisher and collects all pages into a CompletableFuture list,
     * using the specified prefetch limit to control backpressure.
     *
     * @param <T>           the page item type
     * @param publisher     the page publisher to subscribe to
     * @param prefetchLimit the maximum number of pages to request upfront; maintains a window
     *                      by requesting one additional page after each onNext
     * @return a CompletableFuture that completes with the full list of collected pages
     */
    public static <T> CompletableFuture<List<Page<T>>> collectPages(
            SdkPublisher<Page<T>> publisher, int prefetchLimit) {
        List<Page<T>> pages = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<List<Page<T>>> future = new CompletableFuture<>();

        publisher.subscribe(new Subscriber<>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(Math.max(1, prefetchLimit));
            }

            @Override
            public void onNext(Page<T> page) {
                pages.add(page);
                subscription.request(1); // maintain the prefetch window
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(pages);
            }
        });

        return future;
    }
}
