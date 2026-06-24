package com.hogwai.dynamodb.simplified.async;

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * Builder for creating {@link AsyncDynamoSimplifiedClient} instances with
 * a custom DynamoDB async client and/or {@link DynamoDbEnhancedClientExtension extensions}.
 *
 * <p>Use {@link AsyncDynamoSimplifiedClient#builder()} to obtain an instance.</p>
 *
 * <pre>{@code
 * AsyncDynamoSimplifiedClient client = AsyncDynamoSimplifiedClient.builder()
 *     .dynamoDbClient(customClient)
 *     .extensions(myExtension)
 *     .build();
 * }</pre>
 */
public class AsyncDynamoSimplifiedClientBuilder {

    private DynamoDbAsyncClient dynamoDbClient;
    private DynamoDbEnhancedClientExtension[] extensions;

    /** Package-private constructor — called only from {@link AsyncDynamoSimplifiedClient#builder()}. */
    AsyncDynamoSimplifiedClientBuilder() {
    }

    /**
     * Sets a custom DynamoDB async client.
     * <p>If not set, a default {@link DynamoDbAsyncClient#create()} will be used.</p>
     *
     * @param client the DynamoDB async client to use
     * @return this builder
     */
    @NonNull
    public AsyncDynamoSimplifiedClientBuilder dynamoDbClient(@NonNull DynamoDbAsyncClient client) {
        this.dynamoDbClient = Objects.requireNonNull(client, "client must not be null");
        return this;
    }

    /**
     * Sets one or more extensions for the {@link DynamoDbEnhancedAsyncClient}.
     *
     * @param extensions the extensions to register
     * @return this builder
     */
    @NonNull
    public AsyncDynamoSimplifiedClientBuilder extensions(@NonNull DynamoDbEnhancedClientExtension... extensions) {
        this.extensions = Objects.requireNonNull(extensions, "extensions must not be null");
        return this;
    }

    /**
     * Builds the {@link AsyncDynamoSimplifiedClient} with the configured client and extensions.
     *
     * @return a new {@code AsyncDynamoSimplifiedClient} instance
     */
    @NonNull
    public AsyncDynamoSimplifiedClient build() {
        DynamoDbAsyncClient client = this.dynamoDbClient != null ? this.dynamoDbClient : DynamoDbAsyncClient.create();
        DynamoDbEnhancedAsyncClient.Builder enhancedBuilder = DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(client);
        if (extensions != null && extensions.length > 0) {
            enhancedBuilder = enhancedBuilder.extensions(extensions);
        }
        return new AsyncDynamoSimplifiedClient(enhancedBuilder.build(), client);
    }
}
