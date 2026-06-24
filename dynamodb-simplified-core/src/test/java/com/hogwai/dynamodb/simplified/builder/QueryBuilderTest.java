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
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Collections;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryBuilder")
class QueryBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        public String id;

        TestItem() {
        }

        TestItem(String id) {
            this.id = id;
        }
    }

    // ============ Mocks ============

    @Mock
    DynamoDbTable<TestItem> table;

    @Mock
    PageIterable<TestItem> pageIterable;

    @Mock
    DynamoDbIndex<TestItem> index;

    // ============ Helpers ============

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

    /**
     * Creates a mock Page with {@code items()}, {@code count()}, and {@code lastEvaluatedKey()} stubbed.
     * Use this for tests that need full page metadata (count, executeWithPagination, etc.).
     */


    // ============ Key Condition Tests ============

    @Test
    @DisplayName("partitionKey sets keyEqualTo condition and executes")
    void partitionKey() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1"), new TestItem("2")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .execute();

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
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKeyAndSortKeyEquals("pk", "sk")
                .execute();

        assertEquals(1, result.size());

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyBeginsWith sets sortBeginsWith condition")
    void partitionKeyAndSortKeyBeginsWith() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKeyAndSortKeyBeginsWith("pk", "prefix")
                .execute();

        assertEquals(1, result.size());

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyBetween sets sortBetween condition")
    void partitionKeyAndSortKeyBetween() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKeyAndSortKeyBetween("pk", 10, 20)
                .execute();

        assertEquals(1, result.size());

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyGreaterThan sets sortGreaterThan condition")
    void partitionKeyAndSortKeyGreaterThan() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        new QueryBuilder<>(table)
                .partitionKeyAndSortKeyGreaterThan("pk", 5)
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyGreaterThanOrEqual sets sortGreaterThanOrEqualTo condition")
    void partitionKeyAndSortKeyGreaterThanOrEqual() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        new QueryBuilder<>(table)
                .partitionKeyAndSortKeyGreaterThanOrEqual("pk", 5)
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyLessThan sets sortLessThan condition")
    void partitionKeyAndSortKeyLessThan() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        new QueryBuilder<>(table)
                .partitionKeyAndSortKeyLessThan("pk", 5)
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    @Test
    @DisplayName("partitionKeyAndSortKeyLessThanOrEqual sets sortLessThanOrEqualTo condition")
    void partitionKeyAndSortKeyLessThanOrEqual() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        new QueryBuilder<>(table)
                .partitionKeyAndSortKeyLessThanOrEqual("pk", 5)
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertNotNull(captor.getValue().queryConditional());
    }

    // ============ Execution Tests ============

    @Test
    @DisplayName("execute() returns all items from single page (flattened)")
    void execute() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("1"), new TestItem("2")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        List<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .execute();

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).id);
        assertEquals("2", result.get(1).id);
    }

    @Test
    @DisplayName("executeWithPagination() returns first page items + lastEvaluatedKey")
    void executeWithPagination() {
        @SuppressWarnings("unchecked")
        Page<TestItem> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(new TestItem("a"), new TestItem("b")));
        when(page.lastEvaluatedKey()).thenReturn(Map.of("pk", attrVal("nextKey")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

        PagedResult<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeWithPagination();

        assertEquals(2, result.size());
        assertEquals("a", result.getItems().get(0).id);
        assertEquals("b", result.getItems().get(1).id);
        assertNotNull(result.getLastEvaluatedKey());
        assertEquals("nextKey", result.getLastEvaluatedKey().get("pk").s());
        assertTrue(result.hasMorePages());
    }

    @Test
    @DisplayName("executeWithPagination() when no results returns empty PagedResult with null key")
    void executeWithPagination_noResults() {
        @SuppressWarnings("unchecked")
        Page<TestItem> emptyPage = mock(Page.class);
        when(emptyPage.items()).thenReturn(Collections.emptyList());
        when(emptyPage.lastEvaluatedKey()).thenReturn(null);
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(emptyPage));

        PagedResult<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeWithPagination();

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertNull(result.getLastEvaluatedKey());
        assertFalse(result.hasMorePages());
    }

    @Test
    @DisplayName("executeAndGetFirst() returns first item when results exist")
    void executeAndGetFirst_whenResultsExist() {
        Page<TestItem> page = mockPageItems(List.of(new TestItem("first"), new TestItem("second")));
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page));

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
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(emptyPage));

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
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterableOf(page1, page2));

        long total = new QueryBuilder<>(table)
                .partitionKey("pk")
                .count();

        assertEquals(5L, total);
    }

    // ============ Request Verification Tests ============
    //
    // These tests only verify the request structure; they use emptyPageIterable()
    // to avoid creating unnecessary Page mocks and stubbings.

    @Test
    @DisplayName("descending() sets scanIndexForward to false")
    void descending() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .descending()
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertFalse(request.scanIndexForward());
    }

    @Test
    @DisplayName("ascending() sets scanIndexForward to true (default)")
    void ascending() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .ascending()
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertTrue(request.scanIndexForward());
    }

    @Test
    @DisplayName("limit(10) sets limit in request")
    void limit() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .limit(10)
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(10, request.limit());
    }

    @Test
    @DisplayName("filter(c -> c.eq(\"a\", \"b\")) sets filter expression in request")
    void filterWithConsumer() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .filter(c -> c.eq("a", "b"))
                .execute();

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
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        FilterExpression fe = FilterExpression.builder().eq("status", "active");

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .filter(fe)
                .execute();

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
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .filter((FilterExpression) null)
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertNull(request.filterExpression());
    }

    @Test
    @DisplayName("project(\"a\", \"b\") sets attributesToProject in request")
    void projectWithVarargs() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .project("a", "b")
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(List.of("a", "b"), request.attributesToProject());
    }

    @Test
    @DisplayName("project(Consumer) builds projection expression via consumer")
    void projectWithConsumer() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .project(p -> p.include("x", "y"))
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(List.of("x", "y"), request.attributesToProject());
    }

    @Test
    @DisplayName("startFrom(map) sets exclusiveStartKey in request")
    void startFrom() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        Map<String, AttributeValue> startKey = Map.of("k", attrVal("v"));

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .startFrom(startKey)
                .execute();

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
                .execute();

        assertEquals(1, result.size());

        verify(index).query(any(QueryEnhancedRequest.class));
    }

    @Test
    @DisplayName("consistentRead(true) sets consistentRead in request")
    void consistentRead() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .consistentRead(true)
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertTrue(request.consistentRead());
    }

    @Test
    @DisplayName("consistentRead(false) sets consistentRead to false in request")
    void consistentReadFalse() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        new QueryBuilder<>(table)
                .partitionKey("pk")
                .consistentRead(false)
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertFalse(request.consistentRead());
    }

    @Test
    @DisplayName("executeWithPagination() when iterable yields no pages returns empty PagedResult with null key")
    void executeWithPagination_emptyIterable() {
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(emptyPageIterable());

        PagedResult<TestItem> result = new QueryBuilder<>(table)
                .partitionKey("pk")
                .executeWithPagination();

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertNull(result.getLastEvaluatedKey());
        assertFalse(result.hasMorePages());
    }
}
