package dev.hogwai.dynamodb.simplified.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchWriteResult")
class BatchWriteResultTest {

    @Test
    @DisplayName("1-arg constructor returns null consumedCapacity")
    void oneArgConstructor_consumedCapacityIsNull() {
        BatchWriteResult result = new BatchWriteResult(Collections.emptyMap());

        assertNull(result.consumedCapacity(), "consumedCapacity should be null with 1-arg constructor");
    }

    @Test
    @DisplayName("2-arg constructor stores consumed capacity")
    void twoArgConstructor_storesConsumedCapacity() {
        ConsumedCapacity capacity = ConsumedCapacity.builder()
                .tableName("test-table")
                .capacityUnits(10.0)
                .build();

        BatchWriteResult result = new BatchWriteResult(Collections.emptyMap(), capacity);

        assertSame(capacity, result.consumedCapacity(), "should return the same ConsumedCapacity reference");
    }

    @Test
    @DisplayName("implements Consumed interface")
    void implementsConsumed() {
        BatchWriteResult result = new BatchWriteResult(Collections.emptyMap());

        assertInstanceOf(Consumed.class, result, "BatchWriteResult should implement Consumed");
    }

    @Test
    @DisplayName("unprocessedItems are preserved")
    void unprocessedItems() {
        Map<String, List<WriteRequest>> items = Map.of(
                "table1", List.of(WriteRequest.builder().build())
        );

        BatchWriteResult result = new BatchWriteResult(items);

        assertEquals(items, result.unprocessedItems(), "unprocessedItems should match");
        assertTrue(result.hasUnprocessed(), "should have unprocessed items");
    }

    @Test
    @DisplayName("no unprocessed items -> hasUnprocessed is false")
    void noUnprocessedItems() {
        BatchWriteResult result = new BatchWriteResult(Collections.emptyMap());

        assertFalse(result.hasUnprocessed(), "should not have unprocessed items");
    }
}
