package com.hogwai.dynamodb.simplified.builder;

import com.hogwai.dynamodb.simplified.result.PagedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Index")
class IndexTest {

    @Mock
    DynamoDbIndex<Object> dynamoDbIndex;

    @Mock
    Page<Object> page;

    private Index<Object> createIndex() {
        return new Index<>(dynamoDbIndex);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <T> SdkIterable<Page<T>> sdkIterableOf(Page<T>... pages) {
        List<Page<T>> pageList = Arrays.asList(pages);
        return pageList::iterator;
    }

    @Test
    @DisplayName("query() returns a non-null QueryBuilder")
    void queryReturnsBuilder() {
        assertNotNull(createIndex().query());
    }

    @Test
    @DisplayName("scan() returns a non-null ScanBuilder")
    void scanReturnsBuilder() {
        assertNotNull(createIndex().scan());
    }

    @Test
    @DisplayName("query().partitionKey().executeAll() delegates to DynamoDbIndex.query()")
    void queryExecutes() {
        when(dynamoDbIndex.query(any(QueryEnhancedRequest.class))).thenReturn(sdkIterableOf(page));

        Index<Object> idx = createIndex();
        List<Object> results = idx.query().partitionKey("pk").executeAll();

        verify(dynamoDbIndex).query(any(QueryEnhancedRequest.class));
        assertNotNull(results);
    }

    @Test
    @DisplayName("query().executeWithPagination returns PagedResult")
    void queryWithPagination() {
        when(dynamoDbIndex.query(any(QueryEnhancedRequest.class))).thenReturn(sdkIterableOf(page));

        Index<Object> idx = createIndex();
        PagedResult<Object> result = idx.query().partitionKey("pk").executeWithPagination();

        assertNotNull(result);
    }

    @Test
    @DisplayName("scan().executeAll() delegates to DynamoDbIndex.scan()")
    void scanExecutes() {
        when(dynamoDbIndex.scan(any(ScanEnhancedRequest.class))).thenReturn(sdkIterableOf(page));

        Index<Object> idx = createIndex();
        List<Object> results = idx.scan().executeAll();

        verify(dynamoDbIndex).scan(any(ScanEnhancedRequest.class));
        assertNotNull(results);
    }

    @Test
    @DisplayName("indexName() delegates to DynamoDbIndex.indexName()")
    void indexName_delegatesToDynamoDbIndex() {
        when(dynamoDbIndex.indexName()).thenReturn("my-index");

        assertEquals("my-index", createIndex().indexName());

        verify(dynamoDbIndex).indexName();
    }

    @Test
    @DisplayName("tableName() delegates to DynamoDbIndex.tableName()")
    void tableName_delegatesToDynamoDbIndex() {
        when(dynamoDbIndex.tableName()).thenReturn("my-table");

        assertEquals("my-table", createIndex().tableName());

        verify(dynamoDbIndex).tableName();
    }

    @Test
    @DisplayName("getRawIndex() returns the wrapped DynamoDbIndex")
    void getRawIndex_returnsWrappedIndex() {
        assertSame(dynamoDbIndex, createIndex().getRawIndex());
    }
}
