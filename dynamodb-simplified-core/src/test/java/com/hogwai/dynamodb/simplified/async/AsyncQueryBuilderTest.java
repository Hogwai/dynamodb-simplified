package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AsyncQueryBuilder}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncQueryBuilder")
class AsyncQueryBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        public String id;

        TestItem() {
            // test
        }

        TestItem(String id) {
            this.id = id;
        }
    }

    // ============ Mocks ============

    @Mock
    DynamoDbAsyncTable<TestItem> table;

    @Mock
    DynamoDbAsyncIndex<TestItem> index;

    // ============ Helpers ============

    private static AttributeValue attrVal(String s) {
        return AttributeValue.builder().s(s).build();
    }

    /**
     * Creates a {@link PagePublisher} that emits a single page and completes.
     * The publisher is a functional interface (extends {@code Publisher}),
     * so a lambda is used instead of Mockito to avoid unnecessary stubbing issues.
     */
    private static <T> PagePublisher<T> publisherThatEmits(Page<T> page) {
        return subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onNext(page);
            subscriber.onComplete();
        };
    }

    /**
     * Creates a {@link PagePublisher} that completes without emitting any pages.
     */
    private static <T> PagePublisher<T> emptyPublisher() {
        return subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onComplete();
        };
    }

    /**
     * Creates a mock Page with only {@code items()} stubbed.
     */
    @SuppressWarnings("unchecked")
    private static <T> Page<T> mockPageWithItems(List<T> items) {
        Page<T> page = mock(Page.class);
        when(page.items()).thenReturn(items);
        return page;
    }

    // ============ Tests ============

    @Test
    @DisplayName("partitionKey().executeAll() delegates to table.query() and returns items")
    void queryPartitionKeyExecute() {
        Page<TestItem> page = mockPageWithItems(List.of(new TestItem("1"), new TestItem("2")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        List<TestItem> result = new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .executeAll()
                .join();

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).id);
        assertEquals("2", result.get(1).id);
        verify(table).query(any(QueryEnhancedRequest.class));
    }

    @Test
    @DisplayName("executeAndGetFirst() returns Optional with first item")
    void executeAndGetFirst() {
        Page<TestItem> page = mockPageWithItems(List.of(new TestItem("first"), new TestItem("second")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        Optional<TestItem> result = new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .executeAndGetFirst()
                .join();

        assertTrue(result.isPresent());
        assertEquals("first", result.get().id);
    }

    @Test
    @DisplayName("executeAndGetFirst() returns empty when no results")
    void executeAndGetFirst_noResults() {
        Page<TestItem> page = mockPageWithItems(Collections.emptyList());
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        Optional<TestItem> result = new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .executeAndGetFirst()
                .join();

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("count() sums page count across all pages")
    void count() {
        @SuppressWarnings("unchecked")
        Page<TestItem> page = mock(Page.class);
        when(page.count()).thenReturn(5);
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        long total = new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .count()
                .join();

        assertEquals(5L, total);
    }

    @Test
    @DisplayName("executeWithPagination() returns first page with lastEvaluatedKey")
    void executeWithPagination() {
        @SuppressWarnings("unchecked")
        Page<TestItem> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(new TestItem("a")));
        when(page.lastEvaluatedKey()).thenReturn(Map.of("pk", attrVal("nextKey")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        PagedResult<TestItem> result = new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .executeWithPagination()
                .join();

        assertEquals(1, result.size());
        assertEquals("a", result.getItems().getFirst().id);
        assertNotNull(result.getLastEvaluatedKey());
        assertEquals("nextKey", result.getLastEvaluatedKey().get("pk").s());
        assertTrue(result.hasMorePages());
    }

    @Test
    @DisplayName("executeWithPagination() when empty returns empty PagedResult with null key")
    void executeWithPagination_empty() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        PagedResult<TestItem> result = new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .executeWithPagination()
                .join();

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertNull(result.getLastEvaluatedKey());
        assertFalse(result.hasMorePages());
    }

    @Test
    @DisplayName("filter(c -> c.eq(...)) chains filter and sets filter expression in request")
    void filter() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .filter(c -> c.eq("status", "active"))
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertNotNull(request.filterExpression());
        Expression expr = request.filterExpression();
        assertEquals("#n0 = :v0", expr.expression());
        assertEquals(Map.of("#n0", "status"), expr.expressionNames());
        assertEquals(Map.of(":v0", AttributeValue.builder().s("active").build()), expr.expressionValues());
    }

    @Test
    @DisplayName("filter(FilterExpression) overload passes filter through")
    void filterWithFilterExpression() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        FilterExpression fe = FilterExpression.builder().eq("color", "red");

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .filter(fe)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertNotNull(request.filterExpression());
        Expression expr = request.filterExpression();
        assertEquals("#n0 = :v0", expr.expression());
        assertEquals(Map.of("#n0", "color"), expr.expressionNames());
        assertEquals(Map.of(":v0", AttributeValue.builder().s("red").build()), expr.expressionValues());
    }

    @Test
    @DisplayName("filter(null) is null-safe and does not set filter expression")
    void filterNull() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .filter((FilterExpression) null)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertNull(request.filterExpression());
    }

    @Test
    @DisplayName("project(\"a\", \"b\") chains projection and sets attributesToProject in request")
    void project() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .project("a", "b")
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(List.of("a", "b"), request.attributesToProject());
    }

    @Test
    @DisplayName("project(Consumer) builds projection and sets attributesToProject")
    void projectWithConsumer() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .project(p -> p.include("x", "y"))
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(List.of("x", "y"), request.attributesToProject());
    }

    @Test
    @DisplayName("descending() sets scanIndexForward to false in request")
    void descending() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .descending()
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertFalse(request.scanIndexForward());
    }

    @Test
    @DisplayName("ascending() sets scanIndexForward to true (default)")
    void ascending() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .ascending()
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertTrue(request.scanIndexForward());
    }

    @Test
    @DisplayName("limit(10) sets limit in request")
    void limit() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .limit(10)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(10, request.limit());
    }

    @Test
    @DisplayName("startFrom(map) sets exclusiveStartKey in request")
    void startFrom() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        Map<String, AttributeValue> startKey = Map.of("k", attrVal("v"));

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .startFrom(startKey)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(startKey, request.exclusiveStartKey());
    }

    @Test
    @DisplayName("consistentRead(true) sets consistentRead in request")
    void consistentRead() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .consistentRead(true)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertTrue(request.consistentRead());
    }

    @Test
    @DisplayName("consistentRead(false) sets consistentRead to false in request")
    void consistentReadFalse() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .consistentRead(false)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertFalse(request.consistentRead());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyEquals sets keyEqualTo with both pk and sk")
    void partitionKeyAndSortKeyEquals() {
        Page<TestItem> page = mockPageWithItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        List<TestItem> result = new AsyncQueryBuilder<>(table)
                .partitionKeyAndSortKeyEquals("pk", "sk")
                .executeAll()
                .join();

        assertEquals(1, result.size());
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyBeginsWith sets sortBeginsWith condition")
    void partitionKeyAndSortKeyBeginsWith() {
        Page<TestItem> page = mockPageWithItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        List<TestItem> result = new AsyncQueryBuilder<>(table)
                .partitionKeyAndSortKeyBeginsWith("pk", "prefix")
                .executeAll()
                .join();

        assertEquals(1, result.size());
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyBetween sets sortBetween condition")
    void partitionKeyAndSortKeyBetween() {
        Page<TestItem> page = mockPageWithItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        List<TestItem> result = new AsyncQueryBuilder<>(table)
                .partitionKeyAndSortKeyBetween("pk", 10, 20)
                .executeAll()
                .join();

        assertEquals(1, result.size());
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyGreaterThan sets sortGreaterThan condition")
    void partitionKeyAndSortKeyGreaterThan() {
        Page<TestItem> page = mockPageWithItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        new AsyncQueryBuilder<>(table)
                .partitionKeyAndSortKeyGreaterThan("pk", 5)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyGreaterThanOrEqual sets sortGreaterThanOrEqualTo condition")
    void partitionKeyAndSortKeyGreaterThanOrEqual() {
        Page<TestItem> page = mockPageWithItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        new AsyncQueryBuilder<>(table)
                .partitionKeyAndSortKeyGreaterThanOrEqual("pk", 5)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyLessThan sets sortLessThan condition")
    void partitionKeyAndSortKeyLessThan() {
        Page<TestItem> page = mockPageWithItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        new AsyncQueryBuilder<>(table)
                .partitionKeyAndSortKeyLessThan("pk", 5)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyLessThanOrEqual sets sortLessThanOrEqualTo condition")
    void partitionKeyAndSortKeyLessThanOrEqual() {
        Page<TestItem> page = mockPageWithItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        new AsyncQueryBuilder<>(table)
                .partitionKeyAndSortKeyLessThanOrEqual("pk", 5)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("constructWithIndex_executesViaIndexQuery when using DynamoDbAsyncIndex constructor")
    void constructWithIndex() {
        PagePublisher<TestItem> pagePublisher = publisherThatEmits(
                mockPageWithItems(List.of(new TestItem("idx1"))));
        when(index.query(any(QueryEnhancedRequest.class))).thenReturn(pagePublisher);

        CompletableFuture<List<TestItem>> future = new AsyncQueryBuilder<>(index)
                .partitionKey("pk")
                .executeAll();

        List<TestItem> result = future.join();
        assertEquals(1, result.size());
        assertEquals("idx1", result.getFirst().id);
        verify(index).query(any(QueryEnhancedRequest.class));
    }

    @Test
    @DisplayName("returnConsumedCapacity() sets returnConsumedCapacity in request")
    void returnConsumedCapacity_setsOnRequest() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncQueryBuilder<>(table)
                .partitionKey("pk")
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .executeAll()
                .join();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(ReturnConsumedCapacity.TOTAL, request.returnConsumedCapacity());
    }

}
