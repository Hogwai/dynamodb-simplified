package dev.hogwai.dynamodb.simplified.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchGetResult")
class BatchGetResultTest {

    @Test
    @DisplayName("2-arg constructor returns null consumedCapacity")
    void twoArgConstructor_consumedCapacityIsNull() {
        BatchGetResult<String> result = new BatchGetResult<>(List.of(), Collections.emptyMap());

        assertNull(result.consumedCapacity(), "consumedCapacity should be null with 2-arg constructor");
    }

    @Test
    @DisplayName("3-arg constructor stores consumed capacity")
    void threeArgConstructor_storesConsumedCapacity() {
        ConsumedCapacity capacity = ConsumedCapacity.builder()
                .tableName("test-table")
                .capacityUnits(10.0)
                .build();

        BatchGetResult<String> result = new BatchGetResult<>(List.of(), Collections.emptyMap(), capacity);

        assertSame(capacity, result.consumedCapacity(), "should return the same ConsumedCapacity reference");
    }

    @Test
    @DisplayName("implements Consumed interface")
    void implementsConsumed() {
        BatchGetResult<String> result = new BatchGetResult<>(List.of(), Collections.emptyMap());

        assertInstanceOf(Consumed.class, result, "BatchGetResult should implement Consumed");
    }

    @Test
    @DisplayName("items and unprocessedKeys are preserved")
    void itemsAndUnprocessedKeys() {
        List<String> items = List.of("item1");
        Map<String, KeysAndAttributes> unprocessed = Map.of(
                "table2", KeysAndAttributes.builder().build()
        );

        BatchGetResult<String> result = new BatchGetResult<>(items, unprocessed);

        assertEquals(items, result.items(), "items should match");
        assertEquals(unprocessed, result.unprocessedKeys(), "unprocessedKeys should match");
        assertTrue(result.hasUnprocessed(), "should have unprocessed keys");
    }

    @Test
    @DisplayName("no unprocessed keys -> hasUnprocessed is false")
    void noUnprocessedKeys() {
        BatchGetResult<String> result = new BatchGetResult<>(List.of(), Collections.emptyMap());

        assertFalse(result.hasUnprocessed(), "should not have unprocessed keys");
    }
}
