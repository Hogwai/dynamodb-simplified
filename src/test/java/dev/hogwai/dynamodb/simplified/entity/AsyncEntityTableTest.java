package dev.hogwai.dynamodb.simplified.entity;

import dev.hogwai.dynamodb.simplified.async.AsyncQueryBuilder;
import dev.hogwai.dynamodb.simplified.async.AsyncTable;
import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncEntityTableTest {

    @Mock
    private AsyncTable<TestUser> mockAsyncTable;

    private EntitySchema<TestUser> schema;
    private AsyncDefaultEntityTable<TestUser> entityTable;

    @Entity(discriminator = "USER", table = "myapp")
    @KeyPrefix(component = "PK", value = "USER")
    static class TestUser {
        private String pk;
        private String userId;
        private String name;

        TestUser() {
        }

        TestUser(String userId, String name) {
            this.userId = userId;
            this.name = name;
        }

        @DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }

        @KeyComponent(component = "PK")
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @BeforeEach
    void setUp() {
        schema = EntitySchemaReader.read(TestUser.class);
        entityTable = new AsyncDefaultEntityTable<>(mockAsyncTable, schema);
    }

    @Test
    void tableName_shouldReturnFromSchema() {
        assertThat(entityTable.tableName()).isEqualTo("myapp");
    }

    @Test
    void schema_shouldReturnSchema() {
        assertThat(entityTable.schema()).isSameAs(schema);
    }

    @Test
    void put_shouldComputeKeyAndDelegate() {
        TestUser user = new TestUser("abc123", "Alice");
        when(mockAsyncTable.putItem(user)).thenReturn(CompletableFuture.completedFuture(null));

        entityTable.put(user).join();

        // PK should be computed from components: userId -> "USER#abc123"
        assertThat(user.getPk()).isEqualTo("USER#abc123");
        verify(mockAsyncTable).putItem(user);
    }

    @Test
    void get_withPartitionKey_shouldDelegate() {
        TestUser expected = new TestUser("abc123", "Alice");
        when(mockAsyncTable.getItem("USER#abc123"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(expected)));

        TestUser result = entityTable.get("USER#abc123").join();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void get_withPartitionAndSortKey_shouldDelegate() {
        TestUser expected = new TestUser("abc123", "Alice");
        when(mockAsyncTable.getItem("USER#abc123", "POST#def456"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(expected)));

        TestUser result = entityTable.get("USER#abc123", "POST#def456").join();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void get_whenNotFound_shouldReturnNull() {
        when(mockAsyncTable.getItem("NONEXISTENT"))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        TestUser result = entityTable.get("NONEXISTENT").join();

        assertThat(result).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void query_shouldFilterByDiscriminator() {
        var queryBuilder = mock(AsyncQueryBuilder.class);
        when(mockAsyncTable.query()).thenReturn(queryBuilder);
        when(queryBuilder.partitionKey("USER#abc123")).thenReturn(queryBuilder);
        when(queryBuilder.filter(any(java.util.function.Consumer.class))).thenReturn(queryBuilder);
        when(queryBuilder.executeAll())
                .thenReturn(CompletableFuture.completedFuture(List.of(new TestUser("abc123", "Alice"))));

        List<TestUser> results = entityTable.query("USER#abc123").join();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getName()).isEqualTo("Alice");
    }

    @Test
    void deleteEntity_shouldComputeKeysAndDelegate() {
        TestUser user = new TestUser("abc123", "Alice");
        when(mockAsyncTable.deleteItem("USER#abc123"))
                .thenReturn(CompletableFuture.completedFuture(null));

        entityTable.deleteEntity(user).join();

        assertThat(user.getPk()).isEqualTo("USER#abc123");
        verify(mockAsyncTable).deleteItem("USER#abc123");
    }

    @Test
    void delete_withPartitionKey_shouldDelegate() {
        when(mockAsyncTable.deleteItem("USER#abc123"))
                .thenReturn(CompletableFuture.completedFuture(null));

        entityTable.delete("USER#abc123").join();

        verify(mockAsyncTable).deleteItem("USER#abc123");
    }

    @Test
    void delete_withPartitionAndSortKey_shouldDelegate() {
        when(mockAsyncTable.deleteItem("USER#abc123", "POST#def456"))
                .thenReturn(CompletableFuture.completedFuture(null));

        entityTable.delete("USER#abc123", "POST#def456").join();

        verify(mockAsyncTable).deleteItem("USER#abc123", "POST#def456");
    }

    @Test
    void update_shouldComputeKeyAndDelegate() {
        TestUser user = new TestUser("abc123", "Alice Updated");
        TestUser updatedUser = new TestUser("abc123", "Alice Updated");
        when(mockAsyncTable.updateItem(user)).thenReturn(CompletableFuture.completedFuture(updatedUser));

        TestUser result = entityTable.update(user).join();

        assertThat(user.getPk()).isEqualTo("USER#abc123");
        assertThat(result).isSameAs(updatedUser);
        verify(mockAsyncTable).updateItem(user);
    }

    // region SK Branch Coverage Tests

    @Entity(discriminator = "ITEM", table = "myapp")
    @KeyPrefix(component = "PK", value = "ITEM")
    @KeyPrefix(component = "SK", value = "TYPE")
    static class TestItemWithSk {
        private String pk;
        private String sk;
        private String itemId;
        private String typeId;

        TestItemWithSk() {
        }

        TestItemWithSk(String itemId, String typeId) {
            this.itemId = itemId;
            this.typeId = typeId;
        }

        @DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }

        @DynamoDbSortKey
        public String getSk() {
            return sk;
        }

        public void setSk(String sk) {
            this.sk = sk;
        }

        @KeyComponent(component = "PK")
        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        @KeyComponent(component = "SK")
        public String getTypeId() {
            return typeId;
        }

        public void setTypeId(String typeId) {
            this.typeId = typeId;
        }
    }

    @Test
    @DisplayName("put with sort key computes both PK and SK")
    @SuppressWarnings({"unchecked"})
    void put_withSortKey_shouldComputeBothKeys() {
        var schemaWithSk = EntitySchemaReader.read(TestItemWithSk.class);
        var mockAsyncTableWithSk = (AsyncTable<TestItemWithSk>) mock(AsyncTable.class);
        var tableWithSk = new AsyncDefaultEntityTable<>(mockAsyncTableWithSk, schemaWithSk);

        TestItemWithSk item = new TestItemWithSk("item1", "gadget");
        when(mockAsyncTableWithSk.putItem(item)).thenReturn(CompletableFuture.completedFuture(null));

        tableWithSk.put(item).join();

        assertThat(item.getPk()).isEqualTo("ITEM#item1");
        assertThat(item.getSk()).isEqualTo("TYPE#gadget");
        verify(mockAsyncTableWithSk).putItem(item);
    }

    @Test
    @DisplayName("update with sort key computes both PK and SK")
    @SuppressWarnings({"unchecked"})
    void update_withSortKey_shouldComputeBothKeys() {
        var schemaWithSk = EntitySchemaReader.read(TestItemWithSk.class);
        var mockAsyncTableWithSk = (AsyncTable<TestItemWithSk>) mock(AsyncTable.class);
        var tableWithSk = new AsyncDefaultEntityTable<>(mockAsyncTableWithSk, schemaWithSk);

        TestItemWithSk item = new TestItemWithSk("item2", "widget");
        TestItemWithSk updated = new TestItemWithSk("item2", "widget");
        when(mockAsyncTableWithSk.updateItem(item)).thenReturn(CompletableFuture.completedFuture(updated));

        TestItemWithSk result = tableWithSk.update(item).join();

        assertThat(item.getPk()).isEqualTo("ITEM#item2");
        assertThat(item.getSk()).isEqualTo("TYPE#widget");
        assertThat(result).isSameAs(updated);
        verify(mockAsyncTableWithSk).updateItem(item);
    }

    @Test
    @DisplayName("deleteEntity with sort key uses delete with partition and sort key")
    @SuppressWarnings({"unchecked"})
    void deleteEntity_withSortKey_usesDeleteWithBothKeys() {
        var schemaWithSk = EntitySchemaReader.read(TestItemWithSk.class);
        var mockAsyncTableWithSk = (AsyncTable<TestItemWithSk>) mock(AsyncTable.class);
        var tableWithSk = new AsyncDefaultEntityTable<>(mockAsyncTableWithSk, schemaWithSk);

        TestItemWithSk item = new TestItemWithSk("item3", "gadget");
        when(mockAsyncTableWithSk.deleteItem("ITEM#item3", "TYPE#gadget"))
                .thenReturn(CompletableFuture.completedFuture(null));

        tableWithSk.deleteEntity(item).join();

        assertThat(item.getPk()).isEqualTo("ITEM#item3");
        assertThat(item.getSk()).isEqualTo("TYPE#gadget");
        verify(mockAsyncTableWithSk).deleteItem("ITEM#item3", "TYPE#gadget");
    }

    @Test
    @DisplayName("deleteEntity with entity having only PK uses delete with partition key only")
    @SuppressWarnings({"unchecked"})
    void deleteEntity_withOnlyPk_usesDeleteWithPkOnly() {
        // Create an entity that has PK but no SK components — readSk returns null
        var entitySchema = EntitySchemaReader.read(TestUser.class);
        var mockAsyncTableForUser = (AsyncTable<TestUser>) mock(AsyncTable.class);
        var tableForUser = new AsyncDefaultEntityTable<>(mockAsyncTableForUser, entitySchema);

        TestUser user = new TestUser("abc123", "Alice");
        when(mockAsyncTableForUser.deleteItem("USER#abc123"))
                .thenReturn(CompletableFuture.completedFuture(null));

        tableForUser.deleteEntity(user).join();

        assertThat(user.getPk()).isEqualTo("USER#abc123");
        verify(mockAsyncTableForUser).deleteItem("USER#abc123");
    }

    @Test
    @DisplayName("deleteEntity throws OperationFailedException when readPk fails")
    @SuppressWarnings({"unchecked"})
    void deleteEntity_whenReadPkFails_throwsOperationFailedException() {
        var entitySchema = EntitySchemaReader.read(TestUser.class);
        var mockAsyncTableForUser = (AsyncTable<TestUser>) mock(AsyncTable.class);
        var tableForUser = new AsyncDefaultEntityTable<>(mockAsyncTableForUser, entitySchema);

        // Create a broken entity whose pk getter throws
        TestUser brokenUser = new TestUser("abc123", "Alice") {
            @Override
            public String getPk() {
                throw new IllegalStateException("PK getter failed");
            }
        };

        // readPk throws directly (not inside a CompletableFuture), so it propagates as-is
        assertThrows(OperationFailedException.class,
                () -> tableForUser.deleteEntity(brokenUser));
    }
}
// endregion
