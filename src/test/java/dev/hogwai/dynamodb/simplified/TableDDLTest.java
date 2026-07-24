package dev.hogwai.dynamodb.simplified;

import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.DescribeTableEnhancedResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Table DDL")
class TableDDLTest {

    @Mock
    DynamoDbTable<Object> dynamoDbTable;

    @Mock
    DynamoDbEnhancedClient enhancedClient;

    @Mock
    DynamoDbClient dynamoDbClient;

    private Table<Object> createTable() {
        return new Table<>(enhancedClient, dynamoDbTable, dynamoDbClient);
    }

    @Test
    @DisplayName("create() delegates to DynamoDbTable.createTable()")
    void createDelegates() {
        createTable().create();
        verify(dynamoDbTable).createTable();
    }

    @Test
    @DisplayName("create(CreateTableEnhancedRequest) delegates to DynamoDbTable.createTable()")
    void createWithRequestDelegates() {
        CreateTableEnhancedRequest request = CreateTableEnhancedRequest.builder().build();
        createTable().create(request);
        verify(dynamoDbTable).createTable(request);
    }

    @Test
    @DisplayName("delete() delegates to DynamoDbTable.deleteTable()")
    void deleteDelegates() {
        createTable().delete();
        verify(dynamoDbTable).deleteTable();
    }

    @Test
    @DisplayName("describe() delegates to DynamoDbTable.describeTable()")
    void describeDelegates() {
        when(dynamoDbTable.describeTable()).thenReturn(mock(DescribeTableEnhancedResponse.class));
        assertNotNull(createTable().describe());
        verify(dynamoDbTable).describeTable();
    }

    @Test
    @DisplayName("create(null) throws NullPointerException")
    void createNullThrows() {
        Table<Object> table = createTable();
        assertThrows(NullPointerException.class, () -> table.create((CreateTableEnhancedRequest) null));
    }

    @Test
    @DisplayName("exists() returns true when table exists")
    void existsReturnsTrue() {
        when(dynamoDbTable.describeTable()).thenReturn(mock(DescribeTableEnhancedResponse.class));
        assertTrue(createTable().exists());
        verify(dynamoDbTable).describeTable();
    }

    @Test
    @DisplayName("exists() returns false when table does not exist")
    void existsReturnsFalseWhenTableNotFound() {
        when(dynamoDbTable.describeTable()).thenThrow(ResourceNotFoundException.class);
        assertFalse(createTable().exists());
        verify(dynamoDbTable).describeTable();
    }

    @Test
    @DisplayName("exists() propagates non-ResourceNotFoundException exceptions")
    void existsPropagatesOtherExceptions() {
        when(dynamoDbTable.describeTable()).thenThrow(new RuntimeException("connection error"));
        Table<Object> table = createTable();
        assertThrows(RuntimeException.class, table::exists);
        verify(dynamoDbTable).describeTable();
    }

    @Test
    @DisplayName("create(Consumer) delegates to DynamoDbTable.createTable(Consumer)")
    void createWithConsumerDelegates() {
        createTable().create(b -> b.provisionedThroughput(p -> p
                .readCapacityUnits(5L)
                .writeCapacityUnits(10L)));
        verify(dynamoDbTable).createTable(any(Consumer.class));
    }

    @Test
    @DisplayName("create(null Consumer) throws NullPointerException")
    void createNullConsumerThrows() {
        Table<Object> table = createTable();
        assertThrows(NullPointerException.class, () -> table.create((Consumer) null));
    }

    // region TTL Management

    @Test
    @DisplayName("enableTtl() calls dynamoDbClient.updateTimeToLive with correct params")
    void enableTtlDelegates() {
        when(dynamoDbTable.tableName()).thenReturn("test-table");

        createTable().enableTtl("expiresAt");

        verify(dynamoDbClient).updateTimeToLive(UpdateTimeToLiveRequest.builder()
                .tableName("test-table")
                .timeToLiveSpecification(TimeToLiveSpecification.builder()
                        .attributeName("expiresAt")
                        .enabled(true)
                        .build())
                .build());
    }

    @Test
    @DisplayName("disableTtl() calls dynamoDbClient.updateTimeToLive with enabled=false")
    void disableTtlDelegates() {
        when(dynamoDbTable.tableName()).thenReturn("test-table");

        createTable().disableTtl("expiresAt");

        verify(dynamoDbClient).updateTimeToLive(UpdateTimeToLiveRequest.builder()
                .tableName("test-table")
                .timeToLiveSpecification(TimeToLiveSpecification.builder()
                        .attributeName("expiresAt")
                        .enabled(false)
                        .build())
                .build());
    }

    @Test
    @DisplayName("describeTtl() calls dynamoDbClient.describeTimeToLive and returns description")
    void describeTtlDelegates() {
        when(dynamoDbTable.tableName()).thenReturn("test-table");
        TimeToLiveDescription description = mock(TimeToLiveDescription.class);
        DescribeTimeToLiveResponse response = mock(DescribeTimeToLiveResponse.class);
        when(response.timeToLiveDescription()).thenReturn(description);
        when(dynamoDbClient.describeTimeToLive(any(DescribeTimeToLiveRequest.class))).thenReturn(response);

        TimeToLiveDescription result = createTable().describeTtl();

        assertSame(description, result);
        verify(dynamoDbClient).describeTimeToLive(DescribeTimeToLiveRequest.builder()
                .tableName("test-table")
                .build());
    }

    @Test
    @DisplayName("enableTtl() wraps DynamoDbException in OperationFailedException")
    void enableTtlWrapsException() {
        when(dynamoDbTable.tableName()).thenReturn("test-table");
        when(dynamoDbClient.updateTimeToLive(any(UpdateTimeToLiveRequest.class)))
                .thenThrow(mock(DynamoDbException.class));

        Table<Object> table = createTable();
        assertThrows(OperationFailedException.class, () -> table.enableTtl("ttl"));
    }

    @Test
    @DisplayName("describeTtl() wraps DynamoDbException in OperationFailedException")
    void describeTtlWrapsException() {
        when(dynamoDbTable.tableName()).thenReturn("test-table");
        when(dynamoDbClient.describeTimeToLive(any(DescribeTimeToLiveRequest.class)))
                .thenThrow(mock(DynamoDbException.class));

        Table<Object> table = createTable();
        assertThrows(OperationFailedException.class, table::describeTtl);
    }
}
// endregion
