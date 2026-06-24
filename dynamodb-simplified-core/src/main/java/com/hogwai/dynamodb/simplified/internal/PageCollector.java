package com.hogwai.dynamodb.simplified.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

/** Utility for bridging Reactive Streams page publishers to CompletableFuture. */
public final class PageCollector {

    private PageCollector() {
    }

    /** Subscribes to a page publisher and collects all pages into a CompletableFuture list. */
    public static <T> CompletableFuture<List<Page<T>>> collectPages(SdkPublisher<Page<T>> publisher) {
        CompletableFuture<List<Page<T>>> resultFuture = new CompletableFuture<>();
        List<Page<T>> pages = Collections.synchronizedList(new ArrayList<>());
        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }
            @Override
            public void onNext(Page<T> page) {
                pages.add(page);
            }
            @Override
            public void onError(Throwable t) {
                resultFuture.completeExceptionally(t);
            }
            @Override
            public void onComplete() {
                resultFuture.complete(pages);
            }
        });
        return resultFuture;
    }
}
