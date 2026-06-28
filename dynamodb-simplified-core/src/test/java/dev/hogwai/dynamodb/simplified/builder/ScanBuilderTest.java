package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import dev.hogwai.dynamodb.simplified.expression.FilterExpression;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
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
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /**
     * Creates a PageIterable that iterates over the given pages.
     */
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

    /**
     * Configures table.scan(any) to return the given PageIterable.
     */
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
    @DisplayName("executeAndGetFirst() only reads first page when multiple pages exist")
    void executeAndGetFirst_onlyReadsFirstPage() {
        TestItem firstItem = new TestItem();
        firstItem.id = "first";
        @SuppressWarnings("unchecked")
        Page<TestItem> page1 = mock(Page.class);
        when(page1.items()).thenReturn(List.of(firstItem));
        @SuppressWarnings("unchecked")
        Page<TestItem> page2 = mock(Page.class);
        lenient().when(page2.items()).thenThrow(new AssertionError("Second page should not be accessed"));
        stubScanReturns(pageIterable(page1, page2));

        Optional<TestItem> result = new ScanBuilder<>(table).executeAndGetFirst();

        assertTrue(result.isPresent());
        assertEquals("first", result.get().id);
    }

    @Test
    @DisplayName("executeWithPagination() returns first page items + lastEvaluatedKey")
    void executeWithPagination_returnsFirstPageAndKey() {
        Page<TestItem> page1 = mockPage(1, 1, Map.of("key", attrVal("next")));
        Page<TestItem> page2 = mockPage(2, 2, null);
        stubScanReturns(pageIterable(page1, page2));

        PagedResult<TestItem> result = new ScanBuilder<>(table).executeWithPagination();

        assertEquals(1, result.size());
        assertNotNull(result.lastEvaluatedKey());
        assertEquals("next", result.lastEvaluatedKey().get("key").s());
        assertTrue(result.hasMorePages());
    }

    @Test
    @DisplayName("executeWithPagination() when no results returns empty PagedResult with null key")
    void executeWithPagination_noResults() {
        stubScanReturns(pageIterable());

        PagedResult<TestItem> result = new ScanBuilder<>(table).executeWithPagination();

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertNull(result.lastEvaluatedKey());
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

    @Test
    @DisplayName("filter with Map builds equality conditions AND'd together")
    void filter_withMap() {
        stubScanReturns(pageIterable());

        new ScanBuilder<>(table)
                .filter(Map.of("status", "active", "region", "us-east-1"))
                .executeAll();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertNotNull(request.filterExpression());
        Expression expr = request.filterExpression();
        // Two equality conditions joined with AND
        assertTrue(expr.expression().contains("AND"));
        assertTrue(expr.expression().contains("="));
        assertEquals(2, expr.expressionNames().size());
        assertEquals(2, expr.expressionValues().size());
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

    @Test
    @DisplayName("count() with low-level client applies limit option")
    void count_withLowLevelAndLimit() {
        stubLowLevelScanReturns(5);

        new ScanBuilder<>(table, dynamoDbClient)
                .limit(10)
                .count();

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        assertEquals(10, captor.getValue().limit());
    }

    @Test
    @DisplayName("count() with low-level client applies exclusiveStartKey option")
    void count_withLowLevelAndExclusiveStartKey() {
        stubLowLevelScanReturns(5);

        new ScanBuilder<>(table, dynamoDbClient)
                .startFrom(Map.of("pk", attrVal("val")))
                .count();

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        assertNotNull(captor.getValue().exclusiveStartKey());
    }

    @Test
    @DisplayName("count() with low-level client and filter expression sets filter in request")
    void count_withLowLevelAndFilterExpression() {
        stubLowLevelScanReturns(5);

        new ScanBuilder<>(table, dynamoDbClient)
                .filter(c -> c.eq("status", "active"))
                .count();

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        assertNotNull(captor.getValue().filterExpression());
    }

    @Test
    @DisplayName("count() with low-level client applies returnConsumedCapacity option")
    void count_withLowLevelAndReturnConsumedCapacity() {
        stubLowLevelScanReturns(5);

        new ScanBuilder<>(table, dynamoDbClient)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .count();

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        assertEquals(ReturnConsumedCapacity.TOTAL, captor.getValue().returnConsumedCapacity());
    }

    @Test
    @DisplayName("count() with low-level client using index resolves table name from index")
    void count_withLowLevel_usingIndex() {
        lenient().when(index.tableName()).thenReturn("index-table");
        stubLowLevelScanReturns(7);

        long total = new ScanBuilder<>(index, dynamoDbClient)
                .count();

        assertEquals(7L, total);

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        assertEquals("index-table", captor.getValue().tableName());
    }

    @Test
    @DisplayName("count() with low-level client applies parallel scan options")
    void count_withLowLevel_parallelScan() {
        stubLowLevelScanReturns(5);

        new ScanBuilder<>(table, dynamoDbClient)
                .parallelScan(4, 0)
                .count();

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        ScanRequest request = captor.getValue();
        assertNotNull(request.totalSegments());
        assertEquals(4, request.totalSegments().intValue());
    }

    @Test
    @DisplayName("count() with low-level client applies consistent read option")
    void count_withLowLevel_consistentRead() {
        stubLowLevelScanReturns(5);

        new ScanBuilder<>(table, dynamoDbClient)
                .consistentRead(true)
                .count();

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());
        assertTrue(captor.getValue().consistentRead());
    }

    // ============ Routing Guard Tests ============

    @Test
    @DisplayName("executeAll() throws when Select.COUNT is set")
    void executeAll_throwsWithSelectCount() {
        var builder = new ScanBuilder<>(table)
                .select(Select.COUNT);
        assertThrows(IllegalStateException.class, builder::executeAll);
    }

    @Test
    @DisplayName("executeStream() throws when Select.COUNT is set")
    void executeStream_throwsWithSelectCount() {
        var builder = new ScanBuilder<>(table)
                .select(Select.COUNT);
        assertThrows(IllegalStateException.class, builder::executeStream);
    }

    @Test
    @DisplayName("executeWithPagination() throws when Select.COUNT is set")
    void executeWithPagination_throwsWithSelectCount() {
        var builder = new ScanBuilder<>(table)
                .select(Select.COUNT);
        assertThrows(IllegalStateException.class, builder::executeWithPagination);
    }

    @Test
    @DisplayName("executeAndGetFirst() throws when Select.COUNT is set")
    void executeAndGetFirst_throwsWithSelectCount() {
        var builder = new ScanBuilder<>(table)
                .select(Select.COUNT);
        assertThrows(IllegalStateException.class, builder::executeAndGetFirst);
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

    // ============ Exception Paths ============

    @Test
    @DisplayName("executeAll wraps DynamoDbException in OperationFailedException")
    void executeAll_wrapsDynamoDbException() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenThrow(mock(DynamoDbException.class));

        var builder = new ScanBuilder<>(table);
        assertThrows(OperationFailedException.class, builder::executeAll);
    }

    @Test
    @DisplayName("executeAndGetFirst wraps DynamoDbException in OperationFailedException")
    void executeAndGetFirst_wrapsDynamoDbException() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenThrow(mock(DynamoDbException.class));

        var builder = new ScanBuilder<>(table);
        assertThrows(OperationFailedException.class, builder::executeAndGetFirst);
    }

    @Test
    @DisplayName("executeStream wraps DynamoDbException in OperationFailedException")
    void executeStream_wrapsDynamoDbException() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenThrow(mock(DynamoDbException.class));

        var builder = new ScanBuilder<>(table);
        assertThrows(OperationFailedException.class, builder::executeStream);
    }

    @Test
    @DisplayName("executeWithPagination wraps DynamoDbException in OperationFailedException")
    void executeWithPagination_wrapsDynamoDbException() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenThrow(mock(DynamoDbException.class));

        var builder = new ScanBuilder<>(table);
        assertThrows(OperationFailedException.class, builder::executeWithPagination);
    }

    @Test
    @DisplayName("count with enhanced client wraps DynamoDbException in OperationFailedException")
    void count_wrapsDynamoDbException() {
        when(table.scan(any(ScanEnhancedRequest.class))).thenThrow(mock(DynamoDbException.class));

        var builder = new ScanBuilder<>(table);
        assertThrows(OperationFailedException.class, builder::count);
    }

    @Test
    @DisplayName("count with low-level client wraps DynamoDbException in OperationFailedException")
    void count_withLowLevel_wrapsDynamoDbException() {
        when(dynamoDbClient.scan(any(ScanRequest.class))).thenThrow(mock(DynamoDbException.class));

        var builder = new ScanBuilder<>(table, dynamoDbClient);
        assertThrows(OperationFailedException.class, builder::count);
    }

    @Test
    @DisplayName("executeAll with index uses table name from index for getTableName path")
    void executeAll_withIndex_getTableName() {
        Page<TestItem> page = mockPage(1, 1, null);
        lenient().when(index.tableName()).thenReturn("index-table");
        when(index.scan(any(ScanEnhancedRequest.class))).thenReturn(PageIterable.create(() -> List.of(page).iterator()));

        List<TestItem> result = new ScanBuilder<>(index).executeAll();

        assertEquals(1, result.size());
        verify(index).scan(any(ScanEnhancedRequest.class));
    }
}
