package com.hogwai.dynamodb.simplified.result;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds a single page of results from a DynamoDB {@code Query} or {@code Scan}
 * operation as part of the DynamoDB Simplified library.
 * <p>
 * Contains the list of items returned for the current page and the
 * {@code lastEvaluatedKey} that can be passed to a subsequent request to
 * retrieve the next page of results. If {@code lastEvaluatedKey} is
 * {@code null} or empty, there are no more pages to fetch.
 *
 * @param <T> the type of items in the result page
 */
public final class PagedResult<T> implements Consumed {

    private final List<T> items;
    private final @Nullable Map<String, AttributeValue> lastEvaluatedKey;
    private final @Nullable ConsumedCapacity consumedCapacity;

    /**
     * Constructs a new {@code PagedResult} with no consumed capacity information.
     *
     * @param items           the items in this page
     * @param lastEvaluatedKey the last-evaluated key for pagination, or {@code null}
     */
    public PagedResult(@NonNull List<T> items,
                       @Nullable Map<String, AttributeValue> lastEvaluatedKey) {
        this(items, lastEvaluatedKey, null);
    }

    /**
     * Constructs a new {@code PagedResult}.
     *
     * @param items            the items in this page
     * @param lastEvaluatedKey the last-evaluated key for pagination, or {@code null}
     * @param consumedCapacity the consumed capacity, or {@code null}
     */
    public PagedResult(@NonNull List<T> items,
                       @Nullable Map<String, AttributeValue> lastEvaluatedKey,
                       @Nullable ConsumedCapacity consumedCapacity) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.lastEvaluatedKey = lastEvaluatedKey;
        this.consumedCapacity = consumedCapacity;
    }

    /**
     * Returns the consumed capacity, or {@code null} if capacity was not requested
     * or the operation does not support capacity tracking.
     *
     * @return consumed capacity, or {@code null}
     */
    @Override
    @Nullable
    public ConsumedCapacity consumedCapacity() {
        return consumedCapacity;
    }

    /**
     * Returns the list of items in this page.
     *
     * @return the items (never {@code null})
     */
    @NonNull
    public List<T> items() {
        return items;
    }

    /**
     * Returns the last-evaluated key that can be used as the
     * {@code exclusiveStartKey} in a subsequent request to fetch the next page.
     * A return value of {@code null} or an empty map indicates no more pages.
     *
     * @return the last-evaluated key, or {@code null} / empty if this is the
     * last page
     */
    @Nullable
    public Map<String, AttributeValue> lastEvaluatedKey() {
        return lastEvaluatedKey;
    }

    /**
     * Returns whether there are more pages of results available.
     *
     * @return {@code true} if a subsequent request will yield more items,
     * {@code false} otherwise
     */
    public boolean hasMorePages() {
        return lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty();
    }

    /**
     * Returns the number of items in this page.
     *
     * @return the item count
     */
    public int size() {
        return items.size();
    }

    /**
     * Returns whether this page contains zero items.
     *
     * @return {@code true} if the page has no items, {@code false} otherwise
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }
}
