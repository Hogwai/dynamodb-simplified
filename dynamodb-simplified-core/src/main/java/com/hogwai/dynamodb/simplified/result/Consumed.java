package com.hogwai.dynamodb.simplified.result;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

/**
 * Interface for result types that may include consumed capacity information.
 * <p>
 * When {@code returnConsumedCapacity} is set on the builder, the result
 * may contain capacity details that can be retrieved via this interface.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface Consumed {

    /**
     * Returns the consumed capacity, or {@code null} if capacity was not requested
     * or the operation does not support capacity tracking.
     *
     * @return consumed capacity, or {@code null}
     */
    @Nullable ConsumedCapacity consumedCapacity();
}
