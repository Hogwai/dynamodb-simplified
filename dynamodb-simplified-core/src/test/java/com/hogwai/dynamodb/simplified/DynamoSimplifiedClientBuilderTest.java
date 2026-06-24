package com.hogwai.dynamodb.simplified;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoSimplifiedClientBuilder")
class DynamoSimplifiedClientBuilderTest {

    @Mock
    DynamoDbClient dynamoDbClient;

    @Mock
    DynamoDbEnhancedClientExtension extension;

    @Test
    @DisplayName("builder() returns a non-null builder")
    void builderReturnsBuilder() {
        assertNotNull(DynamoSimplifiedClient.builder());
    }

    @Test
    @DisplayName("build() with custom client returns a client using that client")
    void buildWithCustomClient() {
        DynamoSimplifiedClient client = DynamoSimplifiedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        assertNotNull(client);
        assertSame(dynamoDbClient, client.getDynamoDbClient());
    }

    @Test
    @DisplayName("build() with custom client provides a non-null enhanced client")
    void buildWithCustomClientProvidesEnhancedClient() {
        DynamoSimplifiedClient client = DynamoSimplifiedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        assertNotNull(client.getEnhancedClient());
    }

    @Test
    @DisplayName("build() with extensions creates client successfully")
    void buildWithExtensions() {
        DynamoSimplifiedClient client = DynamoSimplifiedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .extensions(extension)
                .build();
        assertNotNull(client);
        assertSame(dynamoDbClient, client.getDynamoDbClient());
    }

    @Test
    @DisplayName("build() with empty extensions array works")
    void buildWithEmptyExtensions() {
        DynamoSimplifiedClient client = DynamoSimplifiedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .extensions()
                .build();
        assertNotNull(client);
    }

    @Test
    @DisplayName("build() with null dynamoDbClient argument throws NullPointerException")
    void buildWithNullClientThrows() {
        var builder = DynamoSimplifiedClient.builder();
        assertThrows(NullPointerException.class, () -> builder.dynamoDbClient(null));
    }

    @Test
    @DisplayName("build() with null extensions argument throws NullPointerException")
    void buildWithNullExtensionsThrows() {
        var builder = DynamoSimplifiedClient.builder();
        assertThrows(NullPointerException.class, () -> builder.extensions((DynamoDbEnhancedClientExtension[]) null));
    }
}
