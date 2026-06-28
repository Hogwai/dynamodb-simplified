package dev.hogwai.dynamodb.simplified.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConditionFailedException")
class ConditionFailedExceptionTest {

    @Test
    @DisplayName("constructor with message sets message")
    void constructorWithMessage() {
        var ex = new ConditionFailedException("condition failed");
        assertEquals("condition failed", ex.getMessage());
    }

    @Test
    @DisplayName("constructor with message and cause sets both")
    void constructorWithMessageAndCause() {
        var cause = new RuntimeException("root");
        var ex = new ConditionFailedException("msg", cause);
        assertEquals("msg", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("fromSdk wraps ConditionalCheckFailedException")
    void fromSdk() {
        var sdkEx = ConditionalCheckFailedException.builder().message("item not found").build();
        var ex = ConditionFailedException.fromSdk(sdkEx);
        assertTrue(ex.getMessage().contains("item not found"));
        assertSame(sdkEx, ex.getCause());
    }
}
