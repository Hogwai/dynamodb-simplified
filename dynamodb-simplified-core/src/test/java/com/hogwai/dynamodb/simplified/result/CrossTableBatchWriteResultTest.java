package com.hogwai.dynamodb.simplified.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrossTableBatchWriteResult")
class CrossTableBatchWriteResultTest {

    @Test
    @DisplayName("constructor without consumed capacity stores unprocessed items as unmodifiable")
    void constructorWithoutConsumedCapacity() {
        Map<String, List<WriteRequest>> items = Map.of("table1", List.of(WriteRequest.builder().build()));
        CrossTableBatchWriteResult result = new CrossTableBatchWriteResult(items);
        assertEquals(items, result.unprocessedItems());
        assertNull(result.consumedCapacity());
    }

    @Test
    @DisplayName("constructor with consumed capacity stores both unprocessed items and capacity")
    void constructorWithConsumedCapacity() {
        Map<String, List<WriteRequest>> items = Map.of("table1", List.of(WriteRequest.builder().build()));
        ConsumedCapacity capacity = ConsumedCapacity.builder().tableName("table1").build();
        CrossTableBatchWriteResult result = new CrossTableBatchWriteResult(items, capacity);
        assertEquals(items, result.unprocessedItems());
        assertSame(capacity, result.consumedCapacity());
    }

    @Test
    @DisplayName("hasUnprocessed returns true when unprocessedItems is not empty")
    void hasUnprocessed_true() {
        Map<String, List<WriteRequest>> items = Map.of("table1", List.of(WriteRequest.builder().build()));
        CrossTableBatchWriteResult result = new CrossTableBatchWriteResult(items);
        assertTrue(result.hasUnprocessed());
    }

    @Test
    @DisplayName("hasUnprocessed returns false when unprocessedItems is empty")
    void hasUnprocessed_false() {
        CrossTableBatchWriteResult result = new CrossTableBatchWriteResult(Collections.emptyMap());
        assertFalse(result.hasUnprocessed());
    }

    @Test
    @DisplayName("unprocessedItems returns the unmodifiable map passed in constructor")
    void unprocessedItems_returnsMap() {
        Map<String, List<WriteRequest>> items = Map.of("table1", List.of());
        CrossTableBatchWriteResult result = new CrossTableBatchWriteResult(items);
        assertThrows(UnsupportedOperationException.class,
                () -> result.unprocessedItems().put("table2", List.of()));
    }
}
