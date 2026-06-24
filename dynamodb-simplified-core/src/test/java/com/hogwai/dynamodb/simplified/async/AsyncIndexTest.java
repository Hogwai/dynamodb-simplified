package com.hogwai.dynamodb.simplified.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AsyncIndex}.
 * <p>
 * Uses Mockito to mock the {@link DynamoDbAsyncIndex} dependency and verifies
 * that the wrapper correctly exposes the underlying index.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncIndex")
class AsyncIndexTest {

    @Mock
    DynamoDbAsyncIndex<Object> dynamoDbAsyncIndex;

    @Test
    @DisplayName("constructor creates a non-null AsyncIndex")
    void constructorWorks() {
        AsyncIndex<Object> index = new AsyncIndex<>(dynamoDbAsyncIndex);
        assertNotNull(index);
    }

    @Test
    @DisplayName("getRawIndex() returns the wrapped DynamoDbAsyncIndex")
    void getRawIndex() {
        AsyncIndex<Object> index = new AsyncIndex<>(dynamoDbAsyncIndex);
        assertSame(dynamoDbAsyncIndex, index.getRawIndex());
    }

    @Test
    @DisplayName("query() returns a non-null AsyncQueryBuilder")
    void queryReturnsBuilder() {
        AsyncIndex<Object> idx = new AsyncIndex<>(dynamoDbAsyncIndex);
        assertNotNull(idx.query());
    }

    @Test
    @DisplayName("scan() returns a non-null AsyncScanBuilder")
    void scanReturnsBuilder() {
        AsyncIndex<Object> idx = new AsyncIndex<>(dynamoDbAsyncIndex);
        assertNotNull(idx.scan());
    }
}
