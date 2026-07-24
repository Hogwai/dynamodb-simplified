package dev.hogwai.dynamodb.simplified.internal;

import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public final class KeyUtils {
    public static Key buildKey(AttributeValue partitionValue, AttributeValue sortValue) {
        Key.Builder builder = Key.builder().partitionValue(partitionValue);
        if (sortValue != null) {
            builder.sortValue(sortValue);
        }
        return builder.build();
    }

    private KeyUtils() {
    }
}
