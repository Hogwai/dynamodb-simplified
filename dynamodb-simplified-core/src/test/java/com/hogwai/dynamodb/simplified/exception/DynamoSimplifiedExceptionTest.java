package com.hogwai.dynamodb.simplified.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("DynamoSimplifiedException")
class DynamoSimplifiedExceptionTest {

    @Test
    @DisplayName("constructor with message sets message")
    void constructorWithMessage() {
        var ex = new DynamoSimplifiedException("test message");
        assertEquals("test message", ex.getMessage());
    }

    @Test
    @DisplayName("constructor with cause sets cause")
    void constructorWithCause() {
        var cause = new RuntimeException("root cause");
        var ex = new DynamoSimplifiedException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("constructor with message and cause sets both")
    void constructorWithMessageAndCause() {
        var cause = new RuntimeException("root");
        var ex = new DynamoSimplifiedException("msg", cause);
        assertEquals("msg", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
