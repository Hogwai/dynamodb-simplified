package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;


import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanBuilder")
class ScanBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        public String id;
    }

    // ============ Mocks ============

    @Mock
    DynamoDbTable<TestItem> table;

    @Mock
    DynamoDbIndex<TestItem> index;

    // ============ Helpers ============

    private static AttributeValue attrVal(String s) {
        return AttributeValue.builder().s(s).build();
    }

    /** Creates a PageIterable that iterates over the given pages. */
    @SafeVarargs
    private static PageIterable<TestItem> pageIterable(Page<TestItem>... pages) {
        return PageIterable.create(() -> List.of(pages).iterator());
    }

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

    /** Configures table.scan(any) to return the given PageIterable. */
    private void stubScanReturns(PageIterable<TestItem> pages) {
        when(table.scan(any(ScanEnhancedRequest.class))).thenReturn(pages);
    }

    // ============ Tests ============

    @Test
    @DisplayName("execute() returns all items from all pages (flattened)")
    void execute() {
        Page<TestItem> page1 = mockPage(1, 1, Map.of("key", attrVal("next")));
        Page<TestItem> page2 = mockPage(2, 2, null);
        stubScanReturns(pageIterable(page1, page2));

        List<TestItem> result = new ScanBuilder<>(table).executeAll();

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("executeAndGetFirst() returns first item from results")
    void executeAndGetFirst() {
        Page<TestItem> page1 = mockPage(1, 1, null);
        stubScanReturns(pageIterable(page1));

        Optional<TestItem> result = new ScanBuilder<>(table).executeAndGetFirst();

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("executeAndGetFirst() returns empty when no results")
    void executeAndGetFirst_empty() {
        stubScanReturns(pageIterable());

        Optional<TestItem> result = new ScanBuilder<>(table).executeAndGetFirst();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("executeWithPagination() returns first page items + lastEvaluatedKey")
    void executeWithPagination_returnsFirstPageAndKey() {
        Page<TestItem> page1 = mockPage(1, 1, Map.of("key", attrVal("next")));
        Page<TestItem> page2 = mockPage(2, 2, null);
        stubScanReturns(pageIterable(page1, page2));

        PagedResult<TestItem> result = new ScanBuilder<>(table).executeWithPagination();

        assertEquals(1, result.size());
        assertNotNull(result.getLastEvaluatedKey());
        assertEquals("next", result.getLastEvaluatedKey().get("key").s());
        assertTrue(result.hasMorePages());
    }

    @Test
    @DisplayName("executeWithPagination() when no results returns empty PagedResult with null key")
    void executeWithPagination_noResults() {
        stubScanReturns(pageIterable());

        PagedResult<TestItem> result = new ScanBuilder<>(table).executeWithPagination();

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertNull(result.getLastEvaluatedKey());
        assertFalse(result.hasMorePages());
    }

    @Test
    @DisplayName("count() sums page counts (1 + 2 = 3)")
    void count() {
        Page<TestItem> page1 = mockPage(0, 1, null);
        Page<TestItem> page2 = mockPage(0, 2, null);
        stubScanReturns(pageIterable(page1, page2));

        long total = new ScanBuilder<>(table).count();

        assertEquals(3L, total);
    }

    @Test
    @DisplayName("filter(c -> c.eq(\"status\", \"active\")) sets filter expression in request")
    void filterWithConsumer() {
        stubScanReturns(pageIterable());

        new ScanBuilder<>(table)
                .filter(c -> c.eq("status", "active"))
                .executeAll();

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
    @DisplayName("project(\"a\", \"b\") sets attributesToProject in request")
    void projectWithVarargs() {
        stubScanReturns(pageIterable());

        new ScanBuilder<>(table)
                .project("a", "b")
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(List.of("a", "b"), request.attributesToProject());
    }

    @Test
    @DisplayName("project(Consumer) builds projection from consumer and sets attributesToProject")
    void projectWithConsumer() {
        stubScanReturns(pageIterable());

        new ScanBuilder<>(table)
                .project(pb -> pb.include("attrFromConsumer"))
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(List.of("attrFromConsumer"), request.attributesToProject());
    }

    @Test
    @DisplayName("limit(100) sets limit in request")
    void limit() {
        stubScanReturns(pageIterable());

        new ScanBuilder<>(table)
                .limit(100)
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(100, request.limit());
    }

    @Test
    @DisplayName("consistentRead(true) sets consistentRead in request")
    void consistentRead() {
        stubScanReturns(pageIterable());

        new ScanBuilder<>(table)
                .consistentRead(true)
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertTrue(request.consistentRead());
    }

    @Test
    @DisplayName("parallelScan(4, 0) sets segment=0 and totalSegments=4 in request")
    void parallelScan() {
        stubScanReturns(pageIterable());

        new ScanBuilder<>(table)
                .parallelScan(4, 0)
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(0, request.segment());
        assertEquals(4, request.totalSegments());
    }

    @Test
    @DisplayName("startFrom(map) sets exclusiveStartKey in request")
    void startFrom() {
        stubScanReturns(pageIterable());

        Map<String, AttributeValue> startKey = Map.of("k", attrVal("x"));

        new ScanBuilder<>(table)
                .startFrom(startKey)
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(startKey, request.exclusiveStartKey());
    }

    @Test
    @DisplayName("filter(null) is null-safe and does not set filter expression")
    void filterNull() {
        stubScanReturns(pageIterable());

        new ScanBuilder<>(table)
                .filter((FilterExpression) null)
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertNull(request.filterExpression());
    }

    @Test
    @DisplayName("filter(FilterExpression) overload passes FilterExpression to request")
    void filterWithFilterExpression() {
        stubScanReturns(pageIterable());

        FilterExpression fe = FilterExpression.builder().eq("status", "active");

        new ScanBuilder<>(table)
                .filter(fe)
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertNotNull(request.filterExpression());
        Expression expr = request.filterExpression();
        assertEquals("#n0 = :v0", expr.expression());
        assertEquals(Map.of("#n0", "status"), expr.expressionNames());
        assertEquals(Map.of(":v0", AttributeValue.builder().s("active").build()), expr.expressionValues());
    }

    // ============ Index Constructor ============

    @Test
    @DisplayName("constructWithIndex_executesViaIndexScan using DynamoDbIndex constructor")
    void constructWithIndex() {
        Page<TestItem> page = mockPage(2, 2, null);
        when(index.scan(any(ScanEnhancedRequest.class))).thenReturn(PageIterable.create(() -> List.of(page).iterator()));

        List<TestItem> result = new ScanBuilder<>(index).executeAll();

        assertEquals(2, result.size());
        verify(index).scan(any(ScanEnhancedRequest.class));
    }

    // ============ returnConsumedCapacity ============

    @Test
    @DisplayName("returnConsumedCapacity(setsOnRequest)")
    void returnConsumedCapacity_setsOnRequest() {
        stubScanReturns(pageIterable());

        new ScanBuilder<>(table)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(ReturnConsumedCapacity.TOTAL, request.returnConsumedCapacity());
    }

    // ============ executeStream ============

    @Test
    @DisplayName("executeStream_returnsLazyStream()")
    void executeStream_returnsLazyStream() {
        Page<TestItem> page1 = mockPage(2, 2, null);
        stubScanReturns(pageIterable(page1));

        Stream<TestItem> stream = new ScanBuilder<>(table).executeStream();

        assertNotNull(stream);
        List<TestItem> result = stream.toList();
        assertEquals(2, result.size());
    }

    // ============ Low-Level Client Tests ============

    @Mock
    DynamoDbClient dynamoDbClient;

    /**
     * Configures dynamoDbClient.scan() to return a response with the given count.
     */
    private void stubLowLevelScanReturns(int count) {
        ScanResponse response = ScanResponse.builder().count(count).build();
        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(response);
    }

    @Test
    @DisplayName("count() with DynamoDbClient uses low-level ScanRequest with Select.COUNT")
    void count_withLowLevelClient() {
        stubLowLevelScanReturns(15);

        long total = new ScanBuilder<>(table, dynamoDbClient)
                .count();

        assertEquals(15L, total);

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        ScanRequest request = captor.getValue();
        assertEquals(Select.COUNT, request.select());
    }

    @Test
    @DisplayName("count() with low-level client and filter includes filter in request")
    void count_withLowLevelClientAndFilter() {
        stubLowLevelScanReturns(5);

        long total = new ScanBuilder<>(table, dynamoDbClient)
                .filter(c -> c.eq("status", "active"))
                .count();

        assertEquals(5L, total);

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        ScanRequest request = captor.getValue();
        assertEquals(Select.COUNT, request.select());
        assertNotNull(request.filterExpression());
    }

    @Test
    @DisplayName("count() with explicit select(Select.COUNT) uses specified Select")
    void count_withExplicitSelectCount() {
        stubLowLevelScanReturns(3);

        long total = new ScanBuilder<>(table, dynamoDbClient)
                .select(Select.COUNT)
                .count();

        assertEquals(3L, total);

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        assertEquals(Select.COUNT, captor.getValue().select());
    }

    // ============ Routing Guard Tests ============

    @Test
    @DisplayName("executeAll() throws when Select.COUNT is set")
    void executeAll_throwsWithSelectCount() {
        assertThrows(IllegalStateException.class, () ->
            new ScanBuilder<>(table)
                .select(Select.COUNT)
                .executeAll()
        );
    }

    @Test
    @DisplayName("executeStream() throws when Select.COUNT is set")
    void executeStream_throwsWithSelectCount() {
        assertThrows(IllegalStateException.class, () ->
            new ScanBuilder<>(table)
                .select(Select.COUNT)
                .executeStream()
        );
    }

    @Test
    @DisplayName("executeWithPagination() throws when Select.COUNT is set")
    void executeWithPagination_throwsWithSelectCount() {
        assertThrows(IllegalStateException.class, () ->
            new ScanBuilder<>(table)
                .select(Select.COUNT)
                .executeWithPagination()
        );
    }

    @Test
    @DisplayName("executeAndGetFirst() throws when Select.COUNT is set")
    void executeAndGetFirst_throwsWithSelectCount() {
        assertThrows(IllegalStateException.class, () ->
            new ScanBuilder<>(table)
                .select(Select.COUNT)
                .executeAndGetFirst()
        );
    }

    @Test
    @DisplayName("count() does NOT throw when Select.COUNT is set")
    void count_doesNotThrowWithSelectCount() {
        stubScanReturns(pageIterable());

        assertDoesNotThrow(() ->
            new ScanBuilder<>(table)
                .select(Select.COUNT)
                .count()
        );
    }
}
