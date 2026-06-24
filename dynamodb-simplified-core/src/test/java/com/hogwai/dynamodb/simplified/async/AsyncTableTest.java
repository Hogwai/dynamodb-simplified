package com.hogwai.dynamodb.simplified.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.DescribeTableEnhancedResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AsyncTable}.
 * <p>
 * Uses Mockito to mock the {@link DynamoDbAsyncTable}, {@link DynamoDbEnhancedAsyncClient},
 * and {@link DynamoDbAsyncClient} dependencies. Verifies that convenience methods delegate
 * to the underlying {@link DynamoDbAsyncTable} and return {@link CompletableFuture}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncTable")
class AsyncTableTest {

    @Mock
    DynamoDbAsyncTable<TestItem> dynamoDbAsyncTable;

    @Mock
    DynamoDbEnhancedAsyncClient enhancedAsyncClient;

    @Mock
    DynamoDbAsyncClient dynamoDbAsyncClient;

    @Mock
    DynamoDbAsyncIndex<TestItem> dynamoDbAsyncIndex;

    /**
     * A simple POJO used as the item type in tests.
     */
    static class TestItem {
        private String id;

        TestItem() {
            /* Required for instantiation */
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    private AsyncTable<TestItem> createTable() {
        return new AsyncTable<>(enhancedAsyncClient, dynamoDbAsyncTable, dynamoDbAsyncClient);
    }

    @Test
    @DisplayName("getItem(pk) delegates to DynamoDbAsyncTable.getItem(Key) and returns Optional")
    void getItemDelegates() {
        TestItem item = new TestItem();
        when(dynamoDbAsyncTable.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(item));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<java.util.Optional<TestItem>> result = table.getItem("pk");

        assertNotNull(result);
        assertEquals(item, result.join().orElse(null));
        verify(dynamoDbAsyncTable).getItem(any(Key.class));
    }

    @Test
    @DisplayName("putItem(item) delegates to DynamoDbAsyncTable.putItem(item)")
    void putItemDelegates() {
        when(dynamoDbAsyncTable.putItem(any(TestItem.class))).thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        TestItem item = new TestItem();
        CompletableFuture<Void> result = table.putItem(item);

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).putItem(item);
    }

    @Test
    @DisplayName("deleteItem(pk) delegates to DynamoDbAsyncTable.deleteItem(Key)")
    void deleteItemDelegates() {
        when(dynamoDbAsyncTable.deleteItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.deleteItem("pk");

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).deleteItem(any(Key.class));
    }

    @Test
    @DisplayName("index(name) returns a non-null AsyncIndex and delegates to DynamoDbAsyncTable.index()")
    void indexDelegates() {
        when(dynamoDbAsyncTable.index(anyString())).thenReturn(dynamoDbAsyncIndex);

        AsyncTable<TestItem> table = createTable();
        AsyncIndex<TestItem> idx = table.index("my-index");

        assertNotNull(idx);
        assertSame(dynamoDbAsyncIndex, idx.getRawIndex());
        verify(dynamoDbAsyncTable).index("my-index");
    }

    @Test
    @DisplayName("create() delegates to DynamoDbAsyncTable.createTable()")
    void createDelegates() {
        when(dynamoDbAsyncTable.createTable()).thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.create();

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).createTable();
    }

    @Test
    @DisplayName("delete() delegates to DynamoDbAsyncTable.deleteTable()")
    void deleteDelegates() {
        when(dynamoDbAsyncTable.deleteTable()).thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.delete();

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).deleteTable();
    }

    @Test
    @DisplayName("describe() delegates to DynamoDbAsyncTable.describeTable()")
    void describeDelegates() {
        DescribeTableEnhancedResponse response = mock(DescribeTableEnhancedResponse.class);
        when(dynamoDbAsyncTable.describeTable()).thenReturn(CompletableFuture.completedFuture(response));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<DescribeTableEnhancedResponse> result = table.describe();

        assertNotNull(result);
        assertSame(response, result.join());
        verify(dynamoDbAsyncTable).describeTable();
    }

    @Test
    @DisplayName("exists() returns true when table exists")
    void existsReturnsTrue() {
        DescribeTableEnhancedResponse response = mock(DescribeTableEnhancedResponse.class);
        when(dynamoDbAsyncTable.describeTable()).thenReturn(CompletableFuture.completedFuture(response));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Boolean> result = table.exists();

        assertTrue(result.join());
        verify(dynamoDbAsyncTable).describeTable();
    }

    @Test
    @DisplayName("exists() returns false when table does not exist")
    void existsReturnsFalseWhenTableNotFound() {
        when(dynamoDbAsyncTable.describeTable())
                .thenReturn(CompletableFuture.failedFuture(ResourceNotFoundException.builder().build()));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Boolean> result = table.exists();

        assertFalse(result.join());
        verify(dynamoDbAsyncTable).describeTable();
    }

    @Test
    @DisplayName("exists() propagates non-ResourceNotFoundException exceptions")
    void existsPropagatesOtherExceptions() {
        when(dynamoDbAsyncTable.describeTable())
                .thenReturn(CompletableFuture.failedFuture(new DynamoSimplifiedException("other error")));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Boolean> result = table.exists();

        CompletionException ex = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(DynamoSimplifiedException.class, ex.getCause());
        verify(dynamoDbAsyncTable).describeTable();
    }

    @Test
    @DisplayName("create(Consumer) delegates to DynamoDbAsyncTable.createTable(Consumer)")
    void createWithConsumerDelegates() {
        when(dynamoDbAsyncTable.createTable(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        AsyncTable<TestItem> table = createTable();
        CompletableFuture<Void> result = table.create(b -> b
                .provisionedThroughput(p -> p
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(10L)));

        assertNotNull(result);
        result.join();
        verify(dynamoDbAsyncTable).createTable(any(Consumer.class));
    }

    @Test
    @DisplayName("create(null Consumer) throws NullPointerException")
    void createNullConsumerThrows() {
        AsyncTable<TestItem> table = createTable();
        assertThrows(NullPointerException.class, () -> table.create((Consumer) null));
    }
}
