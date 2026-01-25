package com.hogwai.dynamodb.simplified.result;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

public class PagedResult<T> {
    private final List<T> items;
    private final Map<String, AttributeValue> lastEvaluatedKey;

    public PagedResult(List<T> items, Map<String, AttributeValue> lastEvaluatedKey) {
        this.items = items;
        this.lastEvaluatedKey = lastEvaluatedKey;
    }

    public List<T> getItems() {
        return items;
    }

    public Map<String, AttributeValue> getLastEvaluatedKey() {
        return lastEvaluatedKey;
    }

    public boolean hasMorePages() {
        return lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty();
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
