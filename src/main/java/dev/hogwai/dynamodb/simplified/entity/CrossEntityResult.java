package dev.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Groups items retrieved from a cross-entity query by their entity type.
 * <p>
 * Each entity type included in the query has its matching items collected
 * in a separate list, accessible by the entity class.
 */
public final class CrossEntityResult {

    private final Map<Class<?>, List<?>> itemsByType;

    CrossEntityResult(Map<Class<?>, List<?>> itemsByType) {
        this.itemsByType = Collections.unmodifiableMap(itemsByType);
    }

    /**
     * Returns the list of items for the given entity type.
     *
     * @param <T>          the entity type
     * @param entityClass  the entity class to retrieve items for
     * @return the list of matching items, or an empty list if none
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <T> List<T> get(@NonNull Class<T> entityClass) {
        List<?> items = itemsByType.get(entityClass);
        return items != null ? (List<T>) items : List.of();
    }

    /**
     * Returns the raw map of entity class to item lists.
     *
     * @return an unmodifiable map of entity types to their items
     */
    @NonNull
    public Map<Class<?>, List<?>> getAll() {
        return itemsByType;
    }

    /**
     * Returns whether all entity type lists in this result are empty.
     *
     * @return {@code true} if no items of any type were returned
     */
    public boolean isEmpty() {
        return itemsByType.values().stream().allMatch(List::isEmpty);
    }

    /**
     * Returns the total number of items across all entity types.
     *
     * @return the total item count
     */
    public int size() {
        return itemsByType.values().stream().mapToInt(List::size).sum();
    }
}
