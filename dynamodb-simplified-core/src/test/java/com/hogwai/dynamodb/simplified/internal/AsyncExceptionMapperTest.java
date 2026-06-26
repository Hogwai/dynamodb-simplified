package com.hogwai.dynamodb.simplified.internal;

import com.hogwai.dynamodb.simplified.exception.ConditionFailedException;
import com.hogwai.dynamodb.simplified.exception.DynamoSimplifiedException;
import com.hogwai.dynamodb.simplified.exception.OperationFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AsyncExceptionMapperTest {

    @Test
    @DisplayName("mapException wraps DynamoDbException into OperationFailedException")
    void mapException_wrapsDynamoDbException() {
        DynamoDbException dde = mock(DynamoDbException.class);
        RuntimeException result = AsyncExceptionMapper.mapException("Query", "MyTable", dde);
        assertInstanceOf(OperationFailedException.class, result);
        assertTrue(result.getMessage().contains("Query"));
        assertTrue(result.getMessage().contains("MyTable"));
    }

    @Test
    @DisplayName("mapException unwraps CompletionException to find DynamoDbException")
    void mapException_unwrapsCompletionException() {
        DynamoDbException dde = mock(DynamoDbException.class);
        CompletionException ce = new CompletionException(dde);
        RuntimeException result = AsyncExceptionMapper.mapException("Scan", "t", ce);
        assertInstanceOf(OperationFailedException.class, result);
    }

    @Test
    @DisplayName("mapException wraps ConditionalCheckFailedException into ConditionFailedException")
    void mapException_wrapsConditionalCheckFailed() {
        ConditionalCheckFailedException ccf = ConditionalCheckFailedException.builder()
                .message("condition failed").build();
        RuntimeException result = AsyncExceptionMapper.mapException("UpdateItem", "t", ccf);
        assertInstanceOf(ConditionFailedException.class, result);
    }

    @Test
    @DisplayName("mapException rethrows RuntimeException as-is")
    void mapException_rethrowsRuntimeException() {
        RuntimeException re = new IllegalArgumentException("bad argument");
        RuntimeException result = AsyncExceptionMapper.mapException("Op", "t", re);
        assertSame(re, result);
    }

    @Test
    @DisplayName("mapException wraps checked exception in DynamoSimplifiedException")
    void mapException_wrapsCheckedException() {
        Exception checked = new Exception("checked failure");
        RuntimeException result = AsyncExceptionMapper.mapException("Op", null, checked);
        assertInstanceOf(DynamoSimplifiedException.class, result);
        assertTrue(result.getMessage().contains("Op failed"));
    }

    @Test
    @DisplayName("handler returns a Function that throws correctly")
    void handler_createsWorkingFunction() {
        DynamoDbException dde = mock(DynamoDbException.class);
        var handler = AsyncExceptionMapper.<String>handler("Query", "t");
        assertThrows(OperationFailedException.class, () -> handler.apply(dde));
    }
}
