package com.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

/**
 * Holds a single page of cross-entity query results along with pagination metadata.
 * <p>
 * Contains the {@link CrossEntityResult} for the current page and the
 * {@code lastEvaluatedKey} that can be passed to a subsequent request to
 * retrieve the next page of results. If {@code lastEvaluatedKey} is
 * {@code null} or empty, there are no more pages to fetch.
 */
public final class CrossEntityResultWithPagination {

    private final CrossEntityResult result;
    private final @Nullable Map<String, AttributeValue> lastEvaluatedKey;

    CrossEntityResultWithPagination(@NonNull CrossEntityResult result,
                                    @Nullable Map<String, AttributeValue> lastEvaluatedKey) {
        this.result = result;
        this.lastEvaluatedKey = lastEvaluatedKey;
    }

    /**
     * Returns the cross-entity results for the current page.
     *
     * @return the result (never {@code null})
     */
    @NonNull
    public CrossEntityResult getResult() {
        return result;
    }

    /**
     * Returns the last-evaluated key that can be used as the
     * {@code exclusiveStartKey} in a subsequent request to fetch the next page.
     * A return value of {@code null} or an empty map indicates no more pages.
     *
     * @return the last-evaluated key, or {@code null} / empty if this is the
     *         last page
     */
    @Nullable
    public Map<String, AttributeValue> getLastEvaluatedKey() {
        return lastEvaluatedKey;
    }

    /**
     * Returns whether there are more pages of results available.
     *
     * @return {@code true} if a subsequent request will yield more items,
     *         {@code false} otherwise
     */
    public boolean hasMore() {
        return lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty();
    }
}
