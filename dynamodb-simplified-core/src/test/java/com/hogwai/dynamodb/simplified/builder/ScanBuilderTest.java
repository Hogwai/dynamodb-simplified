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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;


import java.util.List;
import java.util.Map;

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

        List<TestItem> result = new ScanBuilder<>(table).execute();

        assertEquals(3, result.size());
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
                .execute();

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
                .execute();

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
                .execute();

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
                .execute();

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
                .execute();

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
                .execute();

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
                .execute();

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
                .execute();

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
                .execute();

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

        List<TestItem> result = new ScanBuilder<>(index).execute();

        assertEquals(2, result.size());
        verify(index).scan(any(ScanEnhancedRequest.class));
    }
}
