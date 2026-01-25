package com.hogwai.dynamodb.simplified;

import com.hogwai.dynamodb.simplified.builder.PutBuilder;
import com.hogwai.dynamodb.simplified.builder.QueryBuilder;
import com.hogwai.dynamodb.simplified.builder.ScanBuilder;
import com.hogwai.dynamodb.simplified.builder.UpdateBuilder;
import com.hogwai.dynamodb.simplified.builder.DeleteBuilder;
import com.hogwai.dynamodb.simplified.builder.GetItemBuilder;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Optional;

public class DynamoSimplifiedClient {
    private final DynamoDbEnhancedClient enhancedClient;

    private DynamoSimplifiedClient(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
    }

    public static DynamoSimplifiedClient create() {
        return new DynamoSimplifiedClient(
                DynamoDbEnhancedClient.builder()
                                      .dynamoDbClient(DynamoDbClient.create())
                                      .build());
    }

    public static DynamoSimplifiedClient create(DynamoDbClient client) {
        return new DynamoSimplifiedClient(
                DynamoDbEnhancedClient.builder()
                                      .dynamoDbClient(client)
                                      .build());
    }

    public <T> TableOperations<T> table(String tableName, Class<T> itemClass) {
        TableSchema<T> schema = TableSchema.fromBean(itemClass);
        DynamoDbTable<T> table = enhancedClient.table(tableName, schema);
        return new TableOperations<>(table);
    }

    public <T> TableOperations<T> table(String tableName, TableSchema<T> schema) {
        DynamoDbTable<T> table = enhancedClient.table(tableName, schema);
        return new TableOperations<>(table);
    }

    public DynamoDbEnhancedClient getEnhancedClient() {
        return enhancedClient;
    }

    // ============ Table Operations Wrapper ============

    public static class TableOperations<T> {
        private final DynamoDbTable<T> table;

        TableOperations(DynamoDbTable<T> table) {
            this.table = table;
        }

        // ============ Query ============
        public QueryBuilder<T> query() {
            return new QueryBuilder<>(table);
        }

        // ============ Scan ============

        public ScanBuilder<T> scan() {
            return new ScanBuilder<>(table);
        }

        // ============ Get Item ============

        public Optional<T> get(Object partitionKey) {
            return Optional.ofNullable(
                    table.getItem(Key.builder().partitionValue(toAttributeValue(partitionKey)).build())
            );
        }

        public Optional<T> get(Object partitionKey, Object sortKey) {
            return Optional.ofNullable(
                    table.getItem(Key.builder()
                                     .partitionValue(toAttributeValue(partitionKey))
                                     .sortValue(toAttributeValue(sortKey))
                                     .build())
            );
        }

        public GetItemBuilder<T> getItem(Object partitionKey) {
            return new GetItemBuilder<>(table, partitionKey, null);
        }

        public GetItemBuilder<T> getItem(Object partitionKey, Object sortKey) {
            return new GetItemBuilder<>(table, partitionKey, sortKey);
        }

        // ============ Put Item ============

        public PutBuilder<T> put(T item) {
            return new PutBuilder<>(table, item);
        }

        public void putItem(T item) {
            table.putItem(item);
        }

        // ============ Update Item ============

        public UpdateBuilder<T> update(T item) {
            return new UpdateBuilder<>(table, item);
        }

        public T updateItem(T item) {
            return table.updateItem(item);
        }

        // ============ Delete Item ============

        public DeleteBuilder<T> delete(Object partitionKey) {
            return new DeleteBuilder<>(table, partitionKey, null);
        }

        public DeleteBuilder<T> delete(Object partitionKey, Object sortKey) {
            return new DeleteBuilder<>(table, partitionKey, sortKey);
        }

        public void deleteItem(Object partitionKey) {
            table.deleteItem(Key.builder().partitionValue(toAttributeValue(partitionKey)).build());
        }

        public void deleteItem(Object partitionKey, Object sortKey) {
            table.deleteItem(Key.builder()
                                .partitionValue(toAttributeValue(partitionKey))
                                .sortValue(toAttributeValue(sortKey))
                                .build());
        }

        // ============ Batch Operations ============

//        public BatchWriteBuilder<T> batchWrite() {
//            return new BatchWriteBuilder<>(table);
//        }

        // ============ Transaction Operations ============

//        public TransactWriteBuilder<T> transactWrite() {
//            return new TransactWriteBuilder<>(table);
//        }

        public DynamoDbTable<T> getRawTable() {
            return table;
        }

        private software.amazon.awssdk.services.dynamodb.model.AttributeValue toAttributeValue(Object value) {
            if (value instanceof String) {
                return software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
                                                                                    .s((String) value).build();
            }
            if (value instanceof Number) {
                return software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
                                                                                    .n(value.toString()).build();
            }
            throw new IllegalArgumentException("Unsupported key type: " + value.getClass());
        }
    }
}
