package com.hogwai.dynamodb.simplified;

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Builder for creating {@link DynamoSimplifiedClient} instances with
 * a custom DynamoDB client and/or {@link DynamoDbEnhancedClientExtension extensions}.
 *
 * <p>Use {@link DynamoSimplifiedClient#builder()} to obtain an instance.</p>
 *
 * <pre>{@code
 * DynamoSimplifiedClient client = DynamoSimplifiedClient.builder()
 *     .dynamoDbClient(customClient)
 *     .extensions(myExtension)
 *     .build();
 * }</pre>
 */
public class DynamoSimplifiedClientBuilder {

    private DynamoDbClient dynamoDbClient;
    private DynamoDbEnhancedClientExtension[] extensions;

    /** Package-private constructor — called only from {@link DynamoSimplifiedClient#builder()}. */
    DynamoSimplifiedClientBuilder() {
    }

    /**
     * Sets a custom DynamoDB client.
     * <p>If not set, a default {@link DynamoDbClient#create()} will be used.</p>
     *
     * @param client the DynamoDB client to use
     * @return this builder
     */
    @NonNull
    public DynamoSimplifiedClientBuilder dynamoDbClient(@NonNull DynamoDbClient client) {
        this.dynamoDbClient = Objects.requireNonNull(client, "client must not be null");
        return this;
    }

    /**
     * Sets one or more extensions for the {@link DynamoDbEnhancedClient}.
     *
     * @param extensions the extensions to register
     * @return this builder
     */
    @NonNull
    public DynamoSimplifiedClientBuilder extensions(@NonNull DynamoDbEnhancedClientExtension... extensions) {
        this.extensions = Objects.requireNonNull(extensions, "extensions must not be null");
        return this;
    }

    /**
     * Builds the {@link DynamoSimplifiedClient} with the configured client and extensions.
     *
     * @return a new {@code DynamoSimplifiedClient} instance
     */
    @NonNull
    public DynamoSimplifiedClient build() {
        DynamoDbClient client = this.dynamoDbClient != null ? this.dynamoDbClient : DynamoDbClient.create();
        DynamoDbEnhancedClient.Builder enhancedBuilder = DynamoDbEnhancedClient.builder().dynamoDbClient(client);
        if (extensions != null && extensions.length > 0) {
            enhancedBuilder = enhancedBuilder.extensions(extensions);
        }
        return new DynamoSimplifiedClient(enhancedBuilder.build(), client);
    }
}
