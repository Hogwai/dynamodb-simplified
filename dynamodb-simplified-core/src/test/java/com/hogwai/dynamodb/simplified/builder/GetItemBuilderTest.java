package com.hogwai.dynamodb.simplified.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetItemBuilder")
class GetItemBuilderTest {

    // ============ Test Item ============

    static class TestItem {
        public String id;
        public String name;

        public TestItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // ============ Mocks ============

    @Mock
    private DynamoDbTable<TestItem> table;

    // ============ Builders ============

    private GetItemBuilder<TestItem> pkOnlyBuilder;
    private GetItemBuilder<TestItem> pkSkBuilder;

    @BeforeEach
    void setUp() {
        pkOnlyBuilder = new GetItemBuilder<>(table, "pk-value", null);
        pkSkBuilder = new GetItemBuilder<>(table, "pk-value", "sk-value");
    }

    // ============ Helpers ============

    @SuppressWarnings("unchecked")
    private void mockQueryReturns(TestItem... items) {
        PageIterable<TestItem> pageIterable = mock(PageIterable.class);
        SdkIterable<TestItem> sdkIterable = mock(SdkIterable.class);
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.items()).thenReturn(sdkIterable);
        when(sdkIterable.stream()).thenReturn(Stream.of(items));
    }

    @SuppressWarnings("unchecked")
    private void mockQueryReturnsEmpty() {
        PageIterable<TestItem> pageIterable = mock(PageIterable.class);
        SdkIterable<TestItem> sdkIterable = mock(SdkIterable.class);
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.items()).thenReturn(sdkIterable);
        when(sdkIterable.stream()).thenReturn(Stream.empty());
    }

    // ============ execute() — Simple GetItem ============

    @Test
    @DisplayName("execute() with only partition key calls getItem() with correct key")
    void execute_withPartitionKeyOnly() {
        TestItem expected = new TestItem("pk-value", "item1");
        when(table.getItem(any(GetItemEnhancedRequest.class))).thenReturn(expected);

        Optional<TestItem> result = pkOnlyBuilder.execute();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<GetItemEnhancedRequest> captor =
                ArgumentCaptor.forClass(GetItemEnhancedRequest.class);
        verify(table).getItem(captor.capture());
        GetItemEnhancedRequest request = captor.getValue();

        Key key = request.key();
        assertEquals(AttributeValue.builder().s("pk-value").build(), key.partitionKeyValue());
        assertTrue(key.sortKeyValue().isEmpty());
    }

    @Test
    @DisplayName("execute() with partition and sort key includes both in key")
    void execute_withPartitionAndSortKey() {
        TestItem expected = new TestItem("pk-value", "item2");
        when(table.getItem(any(GetItemEnhancedRequest.class))).thenReturn(expected);

        Optional<TestItem> result = pkSkBuilder.execute();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<GetItemEnhancedRequest> captor =
                ArgumentCaptor.forClass(GetItemEnhancedRequest.class);
        verify(table).getItem(captor.capture());
        GetItemEnhancedRequest request = captor.getValue();

        Key key = request.key();
        assertEquals(AttributeValue.builder().s("pk-value").build(), key.partitionKeyValue());
        assertTrue(key.sortKeyValue().isPresent());
        assertEquals(AttributeValue.builder().s("sk-value").build(), key.sortKeyValue().get());
    }

    @Test
    @DisplayName("execute() returns Optional.empty() when item not found")
    void execute_itemNotFound() {
        when(table.getItem(any(GetItemEnhancedRequest.class))).thenReturn(null);

        Optional<TestItem> result = pkOnlyBuilder.execute();

        assertTrue(result.isEmpty());
        verify(table).getItem(any(GetItemEnhancedRequest.class));
    }

    // ============ execute() — With Projection ============

    @Test
    @DisplayName("execute() with projection calls query() with limit(1) and attributesToProject")
    void execute_withProjection() {
        TestItem expected = new TestItem("pk-value", "proj-item");
        mockQueryReturns(expected);

        Optional<TestItem> result = pkOnlyBuilder
                .project("attr1")
                .execute();

        assertTrue(result.isPresent());
        assertSame(expected, result.get());

        ArgumentCaptor<QueryEnhancedRequest> captor =
                ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(Integer.valueOf(1), request.limit());
        assertNotNull(request.attributesToProject());
        assertTrue(request.attributesToProject().contains("attr1"));
    }

    @Test
    @DisplayName("execute() with projection but no match returns empty")
    void execute_withProjection_noMatch() {
        mockQueryReturnsEmpty();

        Optional<TestItem> result = pkOnlyBuilder
                .project("attr1")
                .execute();

        assertTrue(result.isEmpty());
        verify(table).query(any(QueryEnhancedRequest.class));
        verify(table, never()).getItem(any(GetItemEnhancedRequest.class));
    }

    // ============ consistentRead ============

    @Test
    @DisplayName("consistentRead(true) sets consistentRead on the request")
    void consistentRead() {
        TestItem expected = new TestItem("pk-value", "item-cr");
        when(table.getItem(any(GetItemEnhancedRequest.class))).thenReturn(expected);

        pkOnlyBuilder
                .consistentRead(true)
                .execute();

        ArgumentCaptor<GetItemEnhancedRequest> captor =
                ArgumentCaptor.forClass(GetItemEnhancedRequest.class);
        verify(table).getItem(captor.capture());
        GetItemEnhancedRequest request = captor.getValue();

        assertTrue(request.consistentRead());
    }

    // ============ project(String...) ============

    @Test
    @DisplayName("project(\"attr1\", \"attr2\") passes attribute names to query")
    void project_withVarargs() {
        TestItem expected = new TestItem("pk-value", "proj-varargs");
        mockQueryReturns(expected);

        pkOnlyBuilder
                .project("attr1", "attr2")
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor =
                ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(Integer.valueOf(1), request.limit());
        assertNotNull(request.attributesToProject());
        assertTrue(request.attributesToProject().contains("attr1"));
        assertTrue(request.attributesToProject().contains("attr2"));
    }

    // ============ project(Consumer) ============

    @Test
    @DisplayName("project(Consumer<ProjectionExpression>) includes attribute from consumer")
    void project_withConsumer() {
        TestItem expected = new TestItem("pk-value", "proj-consumer");
        mockQueryReturns(expected);

        pkOnlyBuilder
                .project(pb -> pb.include("consumerAttr"))
                .execute();

        ArgumentCaptor<QueryEnhancedRequest> captor =
                ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest request = captor.getValue();

        assertEquals(Integer.valueOf(1), request.limit());
        assertNotNull(request.attributesToProject());
        assertTrue(request.attributesToProject().contains("consumerAttr"));
    }
}
