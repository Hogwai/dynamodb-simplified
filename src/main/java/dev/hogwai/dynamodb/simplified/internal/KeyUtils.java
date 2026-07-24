package dev.hogwai.dynamodb.simplified.internal;

import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Utility methods for constructing DynamoDB {@link Key} objects.
 */
public final class KeyUtils {

    /**
     * Builds a {@link Key} from partition and optional sort attribute values.
     *
     * @param partitionValue the partition key attribute value
     * @param sortValue      the sort key attribute value, or {@code null} if the table has no sort key
     * @return the constructed key
     */
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
