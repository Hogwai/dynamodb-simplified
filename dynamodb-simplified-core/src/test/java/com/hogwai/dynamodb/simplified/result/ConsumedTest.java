package com.hogwai.dynamodb.simplified.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Consumed interface")
class ConsumedTest {

    @Test
    @DisplayName("anonymous Consumed implementation returns the stored capacity")
    void anonymousImplementation() {
        ConsumedCapacity capacity = ConsumedCapacity.builder()
                .tableName("test")
                .capacityUnits(5.0)
                .build();

        Consumed consumed = () -> capacity;

        assertSame(capacity, consumed.consumedCapacity(), "lambda should return the stored capacity");
    }

    @Test
    @DisplayName("anonymous Consumed implementation returns null")
    void anonymousImplementationNull() {
        Consumed consumed = () -> null;

        assertNull(consumed.consumedCapacity(), "lambda should return null");
    }

    @Test
    @DisplayName("PagedResult is instanceof Consumed")
    void pagedResultIsConsumed() {
        Consumed result = new PagedResult<>(List.of(), null);

        assertInstanceOf(PagedResult.class, result);
        assertNull(result.consumedCapacity());
    }

    @Test
    @DisplayName("BatchGetResult is instanceof Consumed")
    void batchGetResultIsConsumed() {
        Consumed result = new BatchGetResult<>(List.of(), Map.of());

        assertInstanceOf(BatchGetResult.class, result);
        assertNull(result.consumedCapacity());
    }

    @Test
    @DisplayName("BatchWriteResult is instanceof Consumed")
    void batchWriteResultIsConsumed() {
        Consumed result = new BatchWriteResult(Map.of());

        assertInstanceOf(BatchWriteResult.class, result);
        assertNull(result.consumedCapacity());
    }
}
