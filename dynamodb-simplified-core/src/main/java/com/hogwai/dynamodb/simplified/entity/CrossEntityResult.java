package com.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;

import java.util.*;

public final class CrossEntityResult {

    private final Map<Class<?>, List<?>> itemsByType;

    CrossEntityResult(Map<Class<?>, List<?>> itemsByType) {
        this.itemsByType = Collections.unmodifiableMap(itemsByType);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public <T> List<T> get(@NonNull Class<T> entityClass) {
        List<?> items = itemsByType.get(entityClass);
        return items != null ? (List<T>) items : List.of();
    }

    @NonNull
    public Map<Class<?>, List<?>> getAll() {
        return itemsByType;
    }

    public boolean isEmpty() {
        return itemsByType.values().stream().allMatch(List::isEmpty);
    }

    public int size() {
        return itemsByType.values().stream().mapToInt(List::size).sum();
    }
}
