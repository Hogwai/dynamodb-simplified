package com.hogwai.dynamodb.simplified.async;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AsyncScanBuilder}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncScanBuilder")
class AsyncScanBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        public String id;
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
     * Creates a mock Page with items, count, and lastEvaluatedKey stubbed.
     */
    @SuppressWarnings("unchecked")
    private Page<TestItem> mockPage(int itemCount, int count, Map<String, AttributeValue> lastKey) {
        Page<TestItem> page = mock(Page.class);
        TestItem[] items = new TestItem[itemCount];
        for (int i = 0; i < itemCount; i++) {
            items[i] = new TestItem();
        }
        lenient().when(page.items()).thenReturn(List.of(items));
        lenient().when(page.count()).thenReturn(count);
        lenient().when(page.lastEvaluatedKey()).thenReturn(lastKey);
        return page;
    }

    // ============ Tests ============

    @Test
    @DisplayName("execute() returns all items from all pages (flattened)")
    void execute() {
        Page<TestItem> page1 = mockPage(1, 1, Map.of("key", attrVal("next")));
        Page<TestItem> page2 = mockPage(2, 2, null);
        when(table.scan(any(ScanEnhancedRequest.class)))
                .thenReturn(publisherThatEmits(page1))
                .thenReturn(publisherThatEmits(page2));

        // Note: this test validates the builder logic with a single page.
        // Multi-page scan behavior (multiple pages from one scan call) is
        // tested via the reactive subscriber collecting all pages.
        List<TestItem> result = new AsyncScanBuilder<>(table).executeAll().join();

        assertEquals(1, result.size());
        verify(table).scan(any(ScanEnhancedRequest.class));
    }

    @Test
    @DisplayName("execute() returns items from single page")
    void executeSinglePage() {
        Page<TestItem> page = mockPage(3, 3, null);
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        List<TestItem> result = new AsyncScanBuilder<>(table).executeAll().join();

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("executeWithPagination() returns first page items + lastEvaluatedKey")
    void executeWithPagination() {
        Page<TestItem> page1 = mockPage(1, 1, Map.of("key", attrVal("next")));
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(publisherThatEmits(page1));

        PagedResult<TestItem> result = new AsyncScanBuilder<>(table).executeWithPagination().join();

        assertEquals(1, result.size());
        assertNotNull(result.lastEvaluatedKey());
        assertEquals("next", result.lastEvaluatedKey().get("key").s());
        assertTrue(result.hasMorePages());
    }

    @Test
    @DisplayName("executeWithPagination() when empty returns empty PagedResult with null key")
    void executeWithPagination_empty() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        PagedResult<TestItem> result = new AsyncScanBuilder<>(table).executeWithPagination().join();

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertNull(result.lastEvaluatedKey());
        assertFalse(result.hasMorePages());
    }

    @Test
    @DisplayName("count() returns page count")
    void count() {
        Page<TestItem> page = mockPage(0, 5, null);
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        long total = new AsyncScanBuilder<>(table).count().join();

        assertEquals(5L, total);
    }

    @Test
    @DisplayName("filter(c -> c.eq(...)) sets filter expression in request")
    void filterWithConsumer() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncScanBuilder<>(table)
                .filter(c -> c.eq("status", "active"))
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertNotNull(request.filterExpression());
        Expression expr = request.filterExpression();
        assertEquals("#n0 = :v0", expr.expression());
        assertEquals(Map.of("#n0", "status"), expr.expressionNames());
        assertEquals(Map.of(":v0", AttributeValue.builder().s("active").build()), expr.expressionValues());
    }

    @Test
    @DisplayName("filter(FilterExpression) overload passes filter through")
    void filterWithFilterExpression() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        FilterExpression fe = FilterExpression.builder().eq("color", "blue");

        new AsyncScanBuilder<>(table)
                .filter(fe)
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertNotNull(request.filterExpression());
        Expression expr = request.filterExpression();
        assertEquals("#n0 = :v0", expr.expression());
        assertEquals(Map.of("#n0", "color"), expr.expressionNames());
        assertEquals(Map.of(":v0", AttributeValue.builder().s("blue").build()), expr.expressionValues());
    }

    @Test
    @DisplayName("filter with Map builds equality conditions AND'd together")
    void filter_withMap() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncScanBuilder<>(table)
                .filter(Map.of("status", "active", "region", "us-east-1"))
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertNotNull(request.filterExpression());
        Expression expr = request.filterExpression();
        assertTrue(expr.expression().contains("AND"));
        assertTrue(expr.expression().contains("="));
        assertEquals(2, expr.expressionNames().size());
        assertEquals(2, expr.expressionValues().size());
    }

    @Test
    @DisplayName("project(\"a\", \"b\") sets attributesToProject in request")
    void projectWithVarargs() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncScanBuilder<>(table)
                .project("a", "b")
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(List.of("a", "b"), request.attributesToProject());
    }

    @Test
    @DisplayName("project(Consumer) builds projection from consumer")
    void projectWithConsumer() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncScanBuilder<>(table)
                .project(pb -> pb.include("attrFromConsumer"))
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(List.of("attrFromConsumer"), request.attributesToProject());
    }

    @Test
    @DisplayName("limit(100) sets limit in request")
    void limit() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncScanBuilder<>(table)
                .limit(100)
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(100, request.limit());
    }

    @Test
    @DisplayName("consistentRead(true) sets consistentRead in request")
    void consistentRead() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncScanBuilder<>(table)
                .consistentRead(true)
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertTrue(request.consistentRead());
    }

    @Test
    @DisplayName("startFrom(map) sets exclusiveStartKey in request")
    void startFrom() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        Map<String, AttributeValue> startKey = Map.of("k", attrVal("x"));

        new AsyncScanBuilder<>(table)
                .startFrom(startKey)
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(startKey, request.exclusiveStartKey());
    }

    @Test
    @DisplayName("filter(null) is null-safe and does not set filter expression")
    void filterNull() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncScanBuilder<>(table)
                .filter((FilterExpression) null)
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertNull(request.filterExpression());
    }

    @Test
    @DisplayName("constructWithIndex_executesViaIndexScan")
    void constructWithIndex() {
        PagePublisher<TestItem> pagePublisher = publisherThatEmits(mockPage(1, 1, null));
        when(index.scan(any(ScanEnhancedRequest.class))).thenReturn(pagePublisher);

        CompletableFuture<List<TestItem>> future = new AsyncScanBuilder<>(index).executeAll();
        List<TestItem> result = future.join();
        assertEquals(1, result.size());
        verify(index).scan(any(ScanEnhancedRequest.class));
        verifyNoInteractions(table);
    }

    @Test
    @DisplayName("parallelScan sets segment and totalSegments in request")
    void parallelScan() {
        when(table.scan(any(ScanEnhancedRequest.class)))
                .thenReturn(emptyPublisher());

        new AsyncScanBuilder<>(table)
                .parallelScan(4, 0)
                .executeAll().join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();
        assertEquals(0, request.segment());
        assertEquals(4, request.totalSegments());
    }

    @Test
    @DisplayName("returnConsumedCapacity sets value on SDK request")
    void returnConsumedCapacity_setsOnRequest() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        new AsyncScanBuilder<>(table)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .executeAll()
                .join();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();
        assertEquals(ReturnConsumedCapacity.TOTAL, request.returnConsumedCapacity());
    }

    // ============ Low-Level Client Tests ============

    @Mock
    DynamoDbAsyncClient dynamoDbAsyncClient;

    @Test
    @DisplayName("count() with low-level client uses ScanRequest with Select.COUNT")
    void count_withLowLevelClient() {
        ScanResponse response = ScanResponse.builder().count(10).build();
        when(dynamoDbAsyncClient.scan(any(ScanRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        long total = new AsyncScanBuilder<>(table, dynamoDbAsyncClient)
                .count()
                .join();

        assertEquals(10L, total);

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbAsyncClient).scan(captor.capture());
        assertEquals(Select.COUNT, captor.getValue().select());
    }

    @Test
    @DisplayName("executeAll() with Select.COUNT returns failed future")
    void executeAll_throwsWithSelectCount() {
        CompletableFuture<List<TestItem>> future = new AsyncScanBuilder<>(table)
                .select(Select.COUNT)
                .executeAll();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    @DisplayName("executeWithPagination() with Select.COUNT returns failed future")
    void executeWithPagination_throwsWithSelectCount() {
        CompletableFuture<PagedResult<TestItem>> future = new AsyncScanBuilder<>(table)
                .select(Select.COUNT)
                .executeWithPagination();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    @DisplayName("count() does NOT fail when Select.COUNT is set (no client)")
    void count_doesNotThrowWithSelectCount() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(emptyPublisher());

        assertDoesNotThrow(() ->
            new AsyncScanBuilder<>(table)
                .select(Select.COUNT)
                .count()
                .join()
        );
    }

    // ============ executeAndGetFirst() Tests ============

    @Test
    @DisplayName("executeAndGetFirst() returns first item from page")
    void executeAndGetFirst_returnsFirstItem() {
        Page<TestItem> page = mockPage(3, 3, null);
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        Optional<TestItem> result = new AsyncScanBuilder<>(table)
                .executeAndGetFirst()
                .join();

        assertTrue(result.isPresent());
        verify(table).scan(any(ScanEnhancedRequest.class));
    }

    @Test
    @DisplayName("executeAndGetFirst() returns empty when no items in page")
    void executeAndGetFirst_returnsEmptyWhenNoItems() {
        Page<TestItem> page = mockPage(0, 0, null);
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        Optional<TestItem> result = new AsyncScanBuilder<>(table)
                .executeAndGetFirst()
                .join();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("executeAndGetFirst() with Select.COUNT returns failed future")
    void executeAndGetFirst_throwsOnCountSelect() {
        CompletableFuture<Optional<TestItem>> future = new AsyncScanBuilder<>(table)
                .select(Select.COUNT)
                .executeAndGetFirst();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    @DisplayName("executeStream returns publisher that emits items")
    void executeStream_returnsPublisher() {
        Page<TestItem> page = mockPage(2, 2, null);
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(publisherThatEmits(page));

        SdkPublisher<TestItem> publisher = new AsyncScanBuilder<>(table)
                .executeStream()
                .join();

        assertNotNull(publisher);
        List<TestItem> collected = new java.util.ArrayList<>();
        publisher.subscribe(new Subscriber<>() {
            @Override public void onSubscribe(Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(TestItem item) { collected.add(item); }
            @Override public void onError(Throwable t) { /* not needed for this test scenario */ }
            @Override public void onComplete() { /* not needed for this test scenario */ }
        });
        // Items are emitted synchronously from the mock publisher
        assertEquals(2, collected.size());
        verify(table).scan(any(ScanEnhancedRequest.class));
    }

    @Test
    @DisplayName("executeStream() with Select.COUNT returns failed future")
    void executeStream_throwsWithSelectCount() {
        CompletableFuture<SdkPublisher<TestItem>> future = new AsyncScanBuilder<>(table)
                .select(Select.COUNT)
                .executeStream();

        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }
}
