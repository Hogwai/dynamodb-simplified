package dev.hogwai.dynamodb.simplified.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PagedResult")
class PagedResultTest {

    @Test
    @DisplayName("empty items and null lastEvaluatedKey -> isEmpty, size zero, no more pages")
    void emptyItems_nullLastKey() {
        PagedResult<String> result = new PagedResult<>(List.of(), null);

        assertTrue(result.isEmpty(), "should be empty");
        assertEquals(0, result.size(), "size should be zero");
        assertFalse(result.hasMorePages(), "should not have more pages");
        assertTrue(result.items().isEmpty(), "items list should be empty");
    }

    @Test
    @DisplayName("single item and null lastEvaluatedKey -> not empty, size 1, no more pages")
    void singleItem_nullLastKey() {
        PagedResult<String> result = new PagedResult<>(List.of("item1"), null);

        assertEquals(1, result.size(), "size should be 1");
        assertFalse(result.isEmpty(), "should not be empty");
        assertFalse(result.hasMorePages(), "should not have more pages");
    }

    @Test
    @DisplayName("multiple items with non-null lastEvaluatedKey -> items returned, has more pages")
    void multipleItems_nonNullLastKey() {
        List<String> items = List.of("item1", "item2", "item3");
        Map<String, AttributeValue> lastKey = Map.of(
                "pk", AttributeValue.builder().s("key").build()
        );

        PagedResult<String> result = new PagedResult<>(items, lastKey);

        assertEquals(3, result.size(), "size should be 3");
        assertFalse(result.isEmpty(), "should not be empty");
        assertSame(lastKey, result.lastEvaluatedKey(), "should return the same lastEvaluatedKey reference");
        assertEquals(lastKey, result.lastEvaluatedKey(), "lastEvaluatedKey should match");
        assertTrue(result.hasMorePages(), "should have more pages when lastKey is non-null and non-empty");
        assertEquals(items, result.items(), "items should match the input list");
    }

    @Test
    @DisplayName("non-null but empty lastEvaluatedKey map -> hasMorePages is false")
    void nonNullButEmptyLastKey() {
        PagedResult<String> result = new PagedResult<>(
                List.of("item"),
                Collections.emptyMap()
        );

        assertNotNull(result.lastEvaluatedKey(), "lastEvaluatedKey should not be null");
        assertTrue(result.lastEvaluatedKey().isEmpty(), "lastEvaluatedKey should be empty");
        assertFalse(result.hasMorePages(), "should not have more pages when lastKey is empty");
    }

    @Test
    @DisplayName("items list is wrapped in an unmodifiable list")
    void items_isWrappedInUnmodifiableList() {
        List<String> original = new ArrayList<>(List.of("a", "b"));
        PagedResult<String> result = new PagedResult<>(original, null);

        assertNotSame(original, result.items(), "items() should return a different list reference");
        assertEquals(original, result.items(), "items() should contain the same elements");
        // noinspection ImmutableObjectModified
        assertThrows(UnsupportedOperationException.class, () -> result.items().add("c"),
                "items() should return an unmodifiable list");
    }

    @Test
    @DisplayName("2-arg constructor returns null consumedCapacity")
    void twoArgConstructor_consumedCapacityIsNull() {
        PagedResult<String> result = new PagedResult<>(List.of(), null);

        assertNull(result.consumedCapacity(), "consumedCapacity should be null with 2-arg constructor");
    }

    @Test
    @DisplayName("3-arg constructor stores consumed capacity")
    void threeArgConstructor_storesConsumedCapacity() {
        ConsumedCapacity capacity = ConsumedCapacity.builder()
                .tableName("test-table")
                .capacityUnits(10.0)
                .build();

        PagedResult<String> result = new PagedResult<>(List.of(), null, capacity);

        assertSame(capacity, result.consumedCapacity(), "should return the same ConsumedCapacity reference");
    }

    @Test
    @DisplayName("implements Consumed interface")
    void implementsConsumed() {
        PagedResult<String> result = new PagedResult<>(List.of(), null);

        assertInstanceOf(Consumed.class, result, "PagedResult should implement Consumed");
    }
}
