package dev.hogwai.dynamodb.simplified.builder;

import dev.hogwai.dynamodb.simplified.expression.FilterExpression;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryBuilder")
class QueryBuilderTest {

    // region Test Item

    static class TestItem {
        public String id;

        TestItem(String id) {
            this.id = id;
        }
    }

    // endregion

    // region Mocks

    @Mock
    DynamoDbTable<TestItem> table;

    @Mock
    DynamoDbIndex<TestItem> index;

    // endregion

    // region Helpers

    private static AttributeValue attrVal(String s) {
        return AttributeValue.builder().s(s).build();
    }

    /**
     * Creates a concrete PageIterable that yields the given pages.
     * Using a concrete implementation avoids Mockito default-method issues
     * and unnecessary stubbing problems.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <T> PageIterable<T> pageIterableOf(Page<T>... pages) {
        List<Page<T>> pageList = java.util.Arrays.asList(pages);
        return pageList::iterator;
    }

    /**
     * Creates a concrete PageIterable with zero pages.
     */
    private static <T> PageIterable<T> emptyPageIterable() {
        return Collections::emptyIterator;
    }

    /**
     * Creates a mock Page with only {@code items()} stubbed.
     * Use this for tests that only iterate items (execute, executeWithPagination, etc.).
     */
    @SuppressWarnings("unchecked")
    private static <T> Page<T> mockPageItems(List<T> items) {
        Page<T> page = mock(Page.class);
        when(page.items()).thenReturn(items);
        return page;
    }

    // endregion

    // region Mock Setup Helpers

    /**
     * Configures table.query() to return an empty page iterable.
     * Use this for tests that only verify request structure.
     */
    private void stubQueryReturnsEmpty() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());
    }

    /**
     * Configures table.query() to return pages with the given items.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    private void stubQueryReturns(Page<TestItem>... pages) {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(pages));
    }

    // endregion

    // region Key Condition Tests

    @Test
    @DisplayName("partitionKey sets keyEqualTo condition and executes")
    void partitionKey() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1"), new TestItem("2")));
        stubQueryReturns(page);

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeAll();

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).id);
        assertEquals("2", result.get(1).id);

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyEquals sets keyEqualTo with both pk and sk")
    void partitionKeyAndSortKeyEquals() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        stubQueryReturns(page);

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKeyAndSortKeyEquals("pk", "sk")
                .executeAll();

        assertEquals(1, result.size());

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyBeginsWith sets sortBeginsWith condition")
    void partitionKeyAndSortKeyBeginsWith() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        stubQueryReturns(page);

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKeyAndSortKeyBeginsWith("pk", "prefix")
                .executeAll();

        assertEquals(1, result.size());

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyBetween sets sortBetween condition")
    void partitionKeyAndSortKeyBetween() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        stubQueryReturns(page);

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKeyAndSortKeyBetween("pk", 10, 20)
                .executeAll();

        assertEquals(1, result.size());

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyGreaterThan sets sortGreaterThan condition")
    void partitionKeyAndSortKeyGreaterThan() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        stubQueryReturns(page);

        new QueryBuilder<>(table)
                .partitionKeyAndSortKeyGreaterThan("pk", 5)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyGreaterThanOrEqual sets sortGreaterThanOrEqualTo condition")
    void partitionKeyAndSortKeyGreaterThanOrEqual() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        stubQueryReturns(page);

        new QueryBuilder<>(table)
                .partitionKeyAndSortKeyGreaterThanOrEqual("pk", 5)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyLessThan sets sortLessThan condition")
    void partitionKeyAndSortKeyLessThan() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        stubQueryReturns(page);

        new QueryBuilder<>(table)
                .partitionKeyAndSortKeyLessThan("pk", 5)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyLessThanOrEqual sets sortLessThanOrEqualTo condition")
    void partitionKeyAndSortKeyLessThanOrEqual() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        stubQueryReturns(page);

        new QueryBuilder<>(table)
                .partitionKeyAndSortKeyLessThanOrEqual("pk", 5)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    // endregion

    // region Execution Tests

    @Test
    @DisplayName("executeWithPagination() returns first page items + lastEvaluatedKey")
    void executeWithPagination() {
        @SuppressWarnings("unchecked")
        Page<TestItem> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(new TestItem("a"), new TestItem("b")));
        when(page.lastEvaluatedKey()).thenReturn(Map.of("pk", attrVal("nextKey")));
        stubQueryReturns(page);

        PagedResult<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeWithPagination();

        assertEquals(2, result.size());
        assertEquals("a", result.items().get(0).id);
        assertEquals("b", result.items().get(1).id);
        assertNotNull(result.lastEvaluatedKey());
        assertEquals("nextKey", result.lastEvaluatedKey().get("pk").s());
        assertTrue(result.hasMorePages());
    }

    @Test
    @DisplayName("executeWithPagination() when no results returns empty PagedResult with null key")
    void executeWithPagination_noResults() {
        @SuppressWarnings("unchecked")
        Page<TestItem> emptyPage = mock(Page.class);
        when(emptyPage.items()).thenReturn(Collections.emptyList());
        when(emptyPage.lastEvaluatedKey()).thenReturn(null);
        stubQueryReturns(emptyPage);

        PagedResult<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeWithPagination();

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertNull(result.lastEvaluatedKey());
        assertFalse(result.hasMorePages());
    }

    @Test
    @DisplayName("executeAndGetFirst() returns first item when results exist")
    void executeAndGetFirst_whenResultsExist() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("first"), new TestItem("second")));
        stubQueryReturns(page);

        Optional<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeAndGetFirst();

        assertTrue(result.isPresent());
        assertEquals("first", result.get().id);
    }

    @Test
    @DisplayName("executeAndGetFirst() returns empty when no results")
    void executeAndGetFirst_whenNoResults() {
        Page<TestItem> emptyPage = mockPageItems(Collections.emptyList());
        stubQueryReturns(emptyPage);

        Optional<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeAndGetFirst();

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("count() sums page counts across all pages (2 + 3 = 5)")
    void count() {
        @SuppressWarnings("unchecked")
        Page<TestItem> page1 = mock(Page.class);
        when(page1.count()).thenReturn(2);
        @SuppressWarnings("unchecked")
        Page<TestItem> page2 = mock(Page.class);
        when(page2.count()).thenReturn(3);
        stubQueryReturns(page1, page2);

        long total = new QueryBuilder<>(table)
                .partitionKey("pk")
                .count();

        assertEquals(5L, total);
    }

    // endregion

    // region Request Verification Tests
    //
    // These tests only verify the request structure; they use emptyPageIterable()
    // to avoid creating unnecessary Page mocks and stubbings.

    @Test
    @DisplayName("descending() sets scanIndexForward to false")
    void descending() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .descending()
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertFalse(request.scanIndexForward());
    }

    @Test
    @DisplayName("ascending() sets scanIndexForward to true (default)")
    void ascending() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .ascending()
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertTrue(request.scanIndexForward());
    }

    @Test
    @DisplayName("limit(10) sets limit in request")
    void limit() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .limit(10)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(10, request.limit());
    }

    @Test
    @DisplayName("filter(c -> c.eq(\"a\", \"b\")) sets filter expression in request")
    void filterWithConsumer() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .filter(c -> c.eq("a", "b"))
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertNotNull(request.filterExpression());
        Expression expr = request.filterExpression();
        assertEquals("#n0 = :v0", expr.expression());
        assertEquals(Map.of("#n0", "a"), expr.expressionNames());
        assertEquals(Map.of(":v0", AttributeValue.builder().s("b").build()), expr.expressionValues());
    }

    @Test
    @DisplayName("filter(FilterExpression) overload passes FilterExpression to request")
    void filterWithFilterExpression() {
        stubQueryReturnsEmpty();

        FilterExpression fe = FilterExpression.builder().eq("status", "active");

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .filter(fe)
                .executeAll();

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
    @DisplayName("filter(null) is null-safe and does not set filter expression")
    void filterNull() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .filter((FilterExpression) null)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertNull(request.filterExpression());
    }

    @Test
    @DisplayName("project(\"a\", \"b\") sets attributesToProject in request")
    void projectWithVarargs() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .project("a", "b")
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(List.of("a", "b"), request.attributesToProject());
    }

    @Test
    @DisplayName("project(Consumer) builds projection expression via consumer")
    void projectWithConsumer() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .project(p -> p.include("x", "y"))
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(List.of("x", "y"), request.attributesToProject());
    }

    @Test
    @DisplayName("startFrom(map) sets exclusiveStartKey in request")
    void startFrom() {
        stubQueryReturnsEmpty();

        Map<String, AttributeValue> startKey = Map.of("k", attrVal("v"));

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .startFrom(startKey)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(startKey, request.exclusiveStartKey());
    }

    @Test
    @DisplayName("QueryBuilder(DynamoDbIndex) routes query through index.query()")
    void constructWithIndex() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("idx1")));
        when(index.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        List<TestItem> result = new QueryBuilder<>(index)
                .partitionKey("pk")
                .executeAll();

        assertEquals(1, result.size());

        verify(index).query(any(QueryEnhancedRequest.class));
    }

    @Test
    @DisplayName("consistentRead(true) sets consistentRead in request")
    void consistentRead() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .consistentRead(true)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertTrue(request.consistentRead());
    }

    @Test
    @DisplayName("consistentRead(false) sets consistentRead to false in request")
    void consistentReadFalse() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .consistentRead(false)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertFalse(request.consistentRead());
    }

    @Test
    @DisplayName("executeWithPagination() when iterable yields no pages returns empty PagedResult with null key")
    void executeWithPagination_emptyIterable() {
        stubQueryReturnsEmpty();

        PagedResult<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeWithPagination();

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertNull(result.lastEvaluatedKey());
        assertFalse(result.hasMorePages());
    }

    // endregion

    // region ReturnConsumedCapacity / executeAll / executeStream Tests

    @Test
    @DisplayName("returnConsumedCapacity(TOTAL) sets returnConsumedCapacity in request")
    void returnConsumedCapacity_setsOnRequest() {
        stubQueryReturnsEmpty();

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .executeAll();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(ReturnConsumedCapacity.TOTAL, request.returnConsumedCapacity());
    }

    @Test
    @DisplayName("executeAll() returns all items aggregated from pages")
    void executeAll_returnsAllItems() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1"), new TestItem("2")));
        stubQueryReturns(page);

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeAll();

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).id);
        assertEquals("2", result.get(1).id);
    }

    @Test
    @DisplayName("executeStream() returns a lazy stream of items")
    void executeStream_returnsLazyStream() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("a"), new TestItem("b")));
        stubQueryReturns(page);

        Stream<TestItem> stream = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeStream();

        assertNotNull(stream);
        List<TestItem> result = stream.toList();
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).id);
        assertEquals("b", result.get(1).id);
    }

    // endregion

    // region Low-Level Client Tests

    @Mock
    DynamoDbClient dynamoDbClient;

    @Mock
    TableSchema<TestItem> tableSchema;

    @Mock
    TableMetadata tableMetadata;

    /**
     * Configures the table mock to return schema metadata with the given partition
     * and optional sort key names.
     */
    private void mockTableSchema(String skNameOrNull) {
        lenient().when(table.tableSchema()).thenReturn(tableSchema);
        lenient().when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        if (skNameOrNull != null) {
            lenient().when(tableMetadata.primarySortKey()).thenReturn(Optional.of(skNameOrNull));
        } else {
            lenient().when(tableMetadata.primarySortKey()).thenReturn(Optional.empty());
        }
    }

    /**
     * Configures dynamoDbClient.query() to return a response with the given count.
     */
    private void stubLowLevelQueryReturns(int count) {
        QueryResponse response = QueryResponse.builder().count(count).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);
    }

    // endregion

    // region Low-Level Count Tests

    @Test
    @DisplayName("count() with DynamoDbClient uses low-level QueryRequest with Select.COUNT")
    void count_withLowLevelClient() {
        mockTableSchema("sk");
        stubLowLevelQueryReturns(42);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKey("pkVal")
                .partitionKeyAndSortKeyBeginsWith("pkVal", "prefix")
                .count();

        assertEquals(42L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        QueryRequest request = captor.getValue();
        assertEquals(Select.COUNT, request.select());
        assertTrue(request.keyConditionExpression().contains("begins_with"));
    }

    @Test
    @DisplayName("count() with low-level client and partition-key-only uses key eq expression")
    void count_withLowLevelClient_partitionKeyOnly() {
        mockTableSchema(null);
        stubLowLevelQueryReturns(7);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKey("pkVal")
                .count();

        assertEquals(7L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        QueryRequest request = captor.getValue();
        assertEquals(Select.COUNT, request.select());
        assertEquals("#pk = :pk0", request.keyConditionExpression());
    }

    @Test
    @DisplayName("count() with explicit select(Select.COUNT) uses specified Select")
    void count_withExplicitSelectCount() {
        mockTableSchema(null);
        stubLowLevelQueryReturns(3);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKey("pkVal")
                .select(Select.COUNT)
                .count();

        assertEquals(3L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        assertEquals(Select.COUNT, captor.getValue().select());
    }

    @Test
    @DisplayName("count() with select(ALL_PROJECTED_ATTRIBUTES) passes through to low-level request")
    void count_withExplicitSelectAllProjected() {
        mockTableSchema(null);
        stubLowLevelQueryReturns(5);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKey("pkVal")
                .select(Select.ALL_PROJECTED_ATTRIBUTES)
                .count();

        assertEquals(5L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        assertEquals(Select.ALL_PROJECTED_ATTRIBUTES, captor.getValue().select());
    }

    @Test
    @DisplayName("count() with low-level client and filter expression includes filter in request")
    void count_withLowLevelClientAndFilter() {
        mockTableSchema(null);
        stubLowLevelQueryReturns(10);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKey("pkVal")
                .filter(c -> c.eq("status", "active"))
                .count();

        assertEquals(10L, total);
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        assertNotNull(captor.getValue().filterExpression());
    }

    @Test
    @DisplayName("count() with low-level client and sort key BETWEEN condition")
    void count_withLowLevelClient_andSortKeyBetween() {
        mockTableSchema("sk");
        stubLowLevelQueryReturns(42);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKeyAndSortKeyBetween("pk", 1, 10)
                .count();

        assertEquals(42L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        QueryRequest request = captor.getValue();
        assertTrue(request.keyConditionExpression().contains("BETWEEN"));
        assertTrue(request.keyConditionExpression().contains(":sk0"));
        assertTrue(request.keyConditionExpression().contains(":sk1"));
    }

    @Test
    @DisplayName("count() with low-level client and sort key GreaterThan condition")
    void count_withLowLevelClient_andSortKeyGreaterThan() {
        mockTableSchema("sk");
        stubLowLevelQueryReturns(7);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKeyAndSortKeyGreaterThan("pk", 5)
                .count();

        assertEquals(7L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        QueryRequest request = captor.getValue();
        assertTrue(request.keyConditionExpression().contains(" > "));
    }

    @Test
    @DisplayName("count() with low-level client and sort key GreaterThanOrEqual condition")
    void count_withLowLevelClient_andSortKeyGreaterThanOrEqual() {
        mockTableSchema("sk");
        stubLowLevelQueryReturns(7);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKeyAndSortKeyGreaterThanOrEqual("pk", 5)
                .count();

        assertEquals(7L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        QueryRequest request = captor.getValue();
        assertTrue(request.keyConditionExpression().contains(" >= "));
    }

    @Test
    @DisplayName("count() with low-level client and sort key LessThan condition")
    void count_withLowLevelClient_andSortKeyLessThan() {
        mockTableSchema("sk");
        stubLowLevelQueryReturns(7);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKeyAndSortKeyLessThan("pk", 5)
                .count();

        assertEquals(7L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        QueryRequest request = captor.getValue();
        assertTrue(request.keyConditionExpression().contains(" < "));
    }

    @Test
    @DisplayName("count() with low-level client and sort key LessThanOrEqual condition")
    void count_withLowLevelClient_andSortKeyLessThanOrEqual() {
        mockTableSchema("sk");
        stubLowLevelQueryReturns(7);

        long total = new QueryBuilder<>(table, dynamoDbClient)
                .partitionKeyAndSortKeyLessThanOrEqual("pk", 5)
                .count();

        assertEquals(7L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        QueryRequest request = captor.getValue();
        assertTrue(request.keyConditionExpression().contains(" <= "));
    }

    @Test
    @DisplayName("count() with low-level client applies limit option")
    void count_withLowLevelAndLimit() {
        mockTableSchema(null);
        stubLowLevelQueryReturns(5);

        new QueryBuilder<>(table, dynamoDbClient)
                .partitionKey("pkVal")
                .limit(10)
                .count();

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        assertEquals(10, captor.getValue().limit());
    }

    @Test
    @DisplayName("count() with low-level client applies exclusiveStartKey option")
    void count_withLowLevelAndExclusiveStartKey() {
        mockTableSchema(null);
        stubLowLevelQueryReturns(5);

        new QueryBuilder<>(table, dynamoDbClient)
                .partitionKey("pkVal")
                .startFrom(Map.of("pk", attrVal("val")))
                .count();

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        assertNotNull(captor.getValue().exclusiveStartKey());
    }

    @Test
    @DisplayName("count() with low-level client applies scanIndexForward option")
    void count_withLowLevelAndScanIndexForward() {
        mockTableSchema(null);
        stubLowLevelQueryReturns(5);

        new QueryBuilder<>(table, dynamoDbClient)
                .partitionKey("pkVal")
                .descending()
                .count();

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        assertFalse(captor.getValue().scanIndexForward());
    }

    @Test
    @DisplayName("count() with low-level client applies returnConsumedCapacity option")
    void count_withLowLevelAndReturnConsumedCapacity() {
        mockTableSchema(null);
        stubLowLevelQueryReturns(5);

        new QueryBuilder<>(table, dynamoDbClient)
                .partitionKey("pkVal")
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .count();

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        assertEquals(ReturnConsumedCapacity.TOTAL, captor.getValue().returnConsumedCapacity());
    }

    @Test
    @DisplayName("count() with low-level client using index resolves table name from index")
    void count_withLowLevelClient_usingIndex() {
        lenient().when(index.tableSchema()).thenReturn(tableSchema);
        lenient().when(tableSchema.tableMetadata()).thenReturn(tableMetadata);
        lenient().when(tableMetadata.primaryPartitionKey()).thenReturn("pk");
        lenient().when(tableMetadata.primarySortKey()).thenReturn(Optional.empty());
        lenient().when(index.tableName()).thenReturn("index-table");
        stubLowLevelQueryReturns(7);

        long total = new QueryBuilder<>(index, dynamoDbClient)
                .partitionKey("pkVal")
                .count();

        assertEquals(7L, total);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        assertEquals("index-table", captor.getValue().tableName());
    }

    // endregion

    // region Routing Guard Tests

    @Test
    @DisplayName("executeAll() throws when Select.COUNT is set")
    void executeAll_throwsWithSelectCount() {
        var builder = new QueryBuilder<>(table)
                .partitionKey("pk")
                .select(Select.COUNT);
        assertThrows(IllegalStateException.class, builder::executeAll);
    }

    @Test
    @DisplayName("executeStream() throws when Select.COUNT is set")
    void executeStream_throwsWithSelectCount() {
        var builder = new QueryBuilder<>(table)
                .partitionKey("pk")
                .select(Select.COUNT);
        assertThrows(IllegalStateException.class, builder::executeStream);
    }

    @Test
    @DisplayName("executeWithPagination() throws when Select.COUNT is set")
    void executeWithPagination_throwsWithSelectCount() {
        var builder = new QueryBuilder<>(table)
                .partitionKey("pk")
                .select(Select.COUNT);
        assertThrows(IllegalStateException.class, builder::executeWithPagination);
    }

    @Test
    @DisplayName("executeAndGetFirst() only reads first page when multiple pages exist")
    void executeAndGetFirst_onlyReadsFirstPage() {
        Page<TestItem> page1 = mockPageItems(List.of(new TestItem("first")));
        @SuppressWarnings("unchecked")
        Page<TestItem> page2 = mock(Page.class);
        lenient().when(page2.items()).thenThrow(new AssertionError("Second page should not be accessed"));
        stubQueryReturns(page1, page2);

        Optional<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeAndGetFirst();

        assertTrue(result.isPresent());
        assertEquals("first", result.get().id);
    }

    @Test
    @DisplayName("executeAndGetFirst() throws when Select.COUNT is set")
    void executeAndGetFirst_throwsWithSelectCount() {
        var builder = new QueryBuilder<>(table)
                .partitionKey("pk")
                .select(Select.COUNT);
        assertThrows(IllegalStateException.class, builder::executeAndGetFirst);
    }

    @Test
    @DisplayName("count() does NOT throw when Select.COUNT is set")
    void count_doesNotThrowWithSelectCount() {
        // With old constructor (no client), falls through to enhanced path.
        // page.count() needs to be stubbed since count() calls page.count().
        @SuppressWarnings("unchecked")
        Page<TestItem> page = mock(Page.class);
        when(page.count()).thenReturn(3);
        stubQueryReturns(page);

        assertDoesNotThrow(() ->
                new QueryBuilder<>(table)
                        .partitionKey("pk")
                        .select(Select.COUNT)
                        .count()
        );
    }
}
// endregion
