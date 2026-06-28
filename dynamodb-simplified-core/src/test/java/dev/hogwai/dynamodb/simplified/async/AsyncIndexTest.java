package dev.hogwai.dynamodb.simplified.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("indexName() delegates to DynamoDbAsyncIndex.indexName()")
    void indexName_delegatesToDynamoDbAsyncIndex() {
        when(dynamoDbAsyncIndex.indexName()).thenReturn("my-index");

        assertEquals("my-index", new AsyncIndex<>(dynamoDbAsyncIndex).indexName());

        verify(dynamoDbAsyncIndex).indexName();
    }

    @Test
    @DisplayName("tableName() delegates to DynamoDbAsyncIndex.tableName()")
    void tableName_delegatesToDynamoDbAsyncIndex() {
        when(dynamoDbAsyncIndex.tableName()).thenReturn("my-table");

        assertEquals("my-table", new AsyncIndex<>(dynamoDbAsyncIndex).tableName());

        verify(dynamoDbAsyncIndex).tableName();
    }
}
