package dev.hogwai.dynamodb.simplified;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    // region New tests improving branch coverage

    @Test
    @DisplayName("build() succeeds with internally created client and no extensions (created=true default path)")
    void buildWithoutCustomClientAndWithoutExtensions() {
        // Arrange: mock DynamoDbClient.create() and DynamoDbEnhancedClient.builder()
        DynamoDbClient mockClient = mock(DynamoDbClient.class);
        DynamoDbEnhancedClient.Builder mockBuilder = mock(DynamoDbEnhancedClient.Builder.class);
        DynamoDbEnhancedClient enhancedClient = mock(DynamoDbEnhancedClient.class);

        when(mockBuilder.dynamoDbClient(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(enhancedClient);

        try (MockedStatic<DynamoDbClient> clientMock = mockStatic(DynamoDbClient.class);
             MockedStatic<DynamoDbEnhancedClient> enhancedMock = mockStatic(DynamoDbEnhancedClient.class)) {

            clientMock.when(DynamoDbClient::create).thenReturn(mockClient);
            enhancedMock.when(DynamoDbEnhancedClient::builder).thenReturn(mockBuilder);

            // Act
            DynamoSimplifiedClient result = DynamoSimplifiedClient.builder().build();

            // Assert
            assertNotNull(result);
            assertSame(mockClient, result.getDynamoDbClient());
            assertSame(enhancedClient, result.getEnhancedClient());
        }
    }

    @Test
    @DisplayName("build() closes internally created client when enhanced builder throws (created=true, client.close() path)")
    void buildThrowsWithCloseWhenCreatedInternally() {
        // Arrange
        DynamoDbClient mockClient = mock(DynamoDbClient.class);
        DynamoDbEnhancedClient.Builder mockBuilder = mock(DynamoDbEnhancedClient.Builder.class);
        when(mockBuilder.dynamoDbClient(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenThrow(new RuntimeException("enhanced build failed"));

        try (MockedStatic<DynamoDbClient> clientMock = mockStatic(DynamoDbClient.class);
             MockedStatic<DynamoDbEnhancedClient> enhancedMock = mockStatic(DynamoDbEnhancedClient.class)) {

            clientMock.when(DynamoDbClient::create).thenReturn(mockClient);
            enhancedMock.when(DynamoDbEnhancedClient::builder).thenReturn(mockBuilder);

            // Act & Assert
            var builder = DynamoSimplifiedClient.builder();
            // No custom client -> created=true
            RuntimeException thrown = assertThrows(RuntimeException.class, builder::build);
            assertEquals("enhanced build failed", thrown.getMessage());

            // Verify close() was called on the internally created client
            verify(mockClient).close();
        }
    }

    @Test
    @DisplayName("build() does not close user-provided client when enhanced builder throws (created=false, no client.close() path)")
    void buildThrowsWithoutCloseWhenCreatedExternally() {
        // Arrange
        DynamoDbEnhancedClient.Builder mockBuilder = mock(DynamoDbEnhancedClient.Builder.class);
        when(mockBuilder.dynamoDbClient(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenThrow(new RuntimeException("enhanced build failed"));

        try (MockedStatic<DynamoDbEnhancedClient> enhancedMock = mockStatic(DynamoDbEnhancedClient.class)) {
            enhancedMock.when(DynamoDbEnhancedClient::builder).thenReturn(mockBuilder);

            // Act & Assert
            var builder = DynamoSimplifiedClient.builder()
                    .dynamoDbClient(dynamoDbClient);   // User-provided client -> created=false
            RuntimeException thrown = assertThrows(RuntimeException.class, builder::build);
            assertEquals("enhanced build failed", thrown.getMessage());

            // Verify close() was NOT called on the user-provided client
            verify(dynamoDbClient, never()).close();
        }
    }
}
// endregion
