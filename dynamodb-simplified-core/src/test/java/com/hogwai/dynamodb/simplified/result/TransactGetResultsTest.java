package com.hogwai.dynamodb.simplified.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.Document;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactGetResults")
class TransactGetResultsTest {

    @Mock
    Document document;
    @Mock
    DynamoDbTable<String> table;

    @Test
    @DisplayName("get returns item from document when document is non-null")
    void getWithNonNullDocument() {
        when(document.getItem(table)).thenReturn("item");
        var results = new TransactGetResults(List.of(document), List.of(table));

        assertEquals("item", results.get(0));
    }

    @Test
    @DisplayName("get returns null when document.getItem returns null (item does not exist)")
    void getWithNullItem() {
        when(document.getItem(table)).thenReturn(null);
        var results = new TransactGetResults(List.of(document), List.of(table));

        assertNull(results.get(0));
    }

    @Test
    @DisplayName("get returns null when index is out of bounds")
    void getWithOutOfBoundsIndex() {
        var results = new TransactGetResults(List.of(), List.of());

        assertNull(results.get(0));
    }

    @Test
    @DisplayName("size returns number of requested items")
    void size() {
        var results = new TransactGetResults(List.of(document), List.of(table));

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("isEmpty returns true when no items were requested")
    void isEmpty() {
        var results = new TransactGetResults(List.of(), List.of());

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("isEmpty returns false when items were requested")
    void isNotEmpty() {
        var results = new TransactGetResults(List.of(document), List.of(table));

        assertFalse(results.isEmpty());
    }

}
