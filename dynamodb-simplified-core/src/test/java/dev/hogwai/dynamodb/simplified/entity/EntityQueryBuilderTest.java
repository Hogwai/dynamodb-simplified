package dev.hogwai.dynamodb.simplified.entity;

import dev.hogwai.dynamodb.simplified.exception.OperationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityQueryBuilderTest {

    @Mock
    private DynamoDbClient mockClient;

    @Captor
    private ArgumentCaptor<QueryRequest> requestCaptor;

    private EntityQueryBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new EntityQueryBuilder(mockClient, "myapp", "_type");
    }

    @Test
    void execute_shouldBuildCorrectRequest() {
        builder.partitionKey("USER#abc123")
                .includeEntity(UserEntity.class);

        QueryResponse response = QueryResponse.builder()
                .items(List.of(Map.of(
                        "_type", AttributeValue.builder().s("USER").build(),
                        "pk", AttributeValue.builder().s("USER#abc123").build(),
                        "userId", AttributeValue.builder().s("abc123").build()
                )))
                .count(1)
                .build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        CrossEntityResult result = builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.tableName()).isEqualTo("myapp");
        assertThat(request.keyConditionExpression()).contains("#pk");
        assertThat(request.filterExpression()).contains("#dt");
        assertThat(result.get(UserEntity.class)).hasSize(1);
    }

    @Test
    void execute_withMultipleEntityTypes_returnsGroupedResults() {
        QueryResponse response = QueryResponse.builder()
                .items(List.of(
                        Map.of(
                                "_type", AttributeValue.builder().s("USER").build(),
                                "pk", AttributeValue.builder().s("PART#abc").build(),
                                "userId", AttributeValue.builder().s("abc").build()
                        ),
                        Map.of(
                                "_type", AttributeValue.builder().s("POST").build(),
                                "pk", AttributeValue.builder().s("PART#abc").build(),
                                "postId", AttributeValue.builder().s("post1").build()
                        )
                ))
                .count(2)
                .build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.partitionKey("PART#abc")
                .includeEntity(UserEntity.class)
                .includeEntity(PostEntity.class);

        CrossEntityResult result = builder.execute();
        assertThat(result.get(UserEntity.class)).hasSize(1);
        assertThat(result.get(PostEntity.class)).hasSize(1);
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void execute_withoutPartitionKey_shouldThrow() {
        assertThrows(IllegalStateException.class, () -> builder.execute());
    }

    @Test
    void execute_withNoIncludedEntities_returnsEmpty() {
        builder.partitionKey("ANY");
        CrossEntityResult result = builder.execute();
        assertThat(result.isEmpty()).isTrue();
        verify(mockClient, never()).query(any(QueryRequest.class));
    }

    @Test
    void execute_shouldIncludeDiscriminatorFilter() {
        builder.partitionKey("USER#xyz")
                .includeEntity(UserEntity.class);

        QueryResponse response = QueryResponse.builder()
                .items(List.of())
                .count(0)
                .build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.filterExpression()).contains("#dt");
        assertThat(request.filterExpression()).contains(":v0");
        assertThat(request.expressionAttributeNames()).containsEntry("#dt", "_type");
        assertThat(request.expressionAttributeValues()).containsKey(":v0");
        assertThat(request.expressionAttributeValues().get(":v0").s()).isEqualTo("USER");
    }

    @Test
    void get_withUnknownEntityType_returnsEmptyList() {
        builder.partitionKey("PK").includeEntity(UserEntity.class);

        when(mockClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of()).build());

        CrossEntityResult result = builder.execute();
        assertThat(result.get(UserEntity.class)).isEmpty();
    }

    // ============ Sort Key Condition Tests ============

    @Test
    void sortKeyBeginsWith_addsCorrectKeyConditionExpression() {
        builder.partitionKey("USER#abc")
                .includeEntity(EntityWithSk.class)
                .sortKeyBeginsWith("PREFIX");

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.keyConditionExpression()).contains("begins_with(#sk, :sk0)");
        assertThat(request.expressionAttributeNames()).containsKey("#sk");
        assertThat(request.expressionAttributeValues()).containsKey(":sk0");
        assertThat(request.expressionAttributeValues().get(":sk0").s()).isEqualTo("PREFIX");
    }

    @Test
    void sortKeyEquals_addsEQKeyCondition() {
        builder.partitionKey("USER#abc")
                .includeEntity(EntityWithSk.class)
                .sortKeyEquals("EXACT");

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.keyConditionExpression()).contains("#sk = :sk0");
        assertThat(request.expressionAttributeValues().get(":sk0").s()).isEqualTo("EXACT");
    }

    @Test
    void sortKeyBetween_addsBETWEENKeyCondition() {
        builder.partitionKey("USER#abc")
                .includeEntity(EntityWithSk.class)
                .sortKeyBetween("A", "Z");

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.keyConditionExpression()).contains("#sk BETWEEN :sk0 AND :sk1");
        assertThat(request.expressionAttributeValues().get(":sk0").s()).isEqualTo("A");
        assertThat(request.expressionAttributeValues().get(":sk1").s()).isEqualTo("Z");
    }

    @Test
    void sortKeyGreaterThan_addsGTKeyCondition() {
        builder.partitionKey("USER#abc")
                .includeEntity(EntityWithSk.class)
                .sortKeyGreaterThan("THRESHOLD");

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.keyConditionExpression()).contains("#sk > :sk0");
    }

    @Test
    void sortKeyGreaterThanOrEqual_addsGEKeyCondition() {
        builder.partitionKey("USER#abc")
                .includeEntity(EntityWithSk.class)
                .sortKeyGreaterThanOrEqual("THRESHOLD");

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.keyConditionExpression()).contains("#sk >= :sk0");
    }

    @Test
    void sortKeyLessThan_addsLTKeyCondition() {
        builder.partitionKey("USER#abc")
                .includeEntity(EntityWithSk.class)
                .sortKeyLessThan("THRESHOLD");

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.keyConditionExpression()).contains("#sk < :sk0");
    }

    @Test
    void sortKeyLessThanOrEqual_addsLEKeyCondition() {
        builder.partitionKey("USER#abc")
                .includeEntity(EntityWithSk.class)
                .sortKeyLessThanOrEqual("THRESHOLD");

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.keyConditionExpression()).contains("#sk <= :sk0");
    }

    // ============ Pagination Tests ============

    @Test
    void executeWithPagination_returnsFirstPageWithLastEvaluatedKey() {
        Map<String, AttributeValue> lastKey = Map.of("pk", AttributeValue.builder().s("lastKey").build());
        QueryResponse page1 = QueryResponse.builder()
                .items(List.of(Map.of(
                        "_type", AttributeValue.builder().s("USER").build(),
                        "pk", AttributeValue.builder().s("USER#1").build(),
                        "userId", AttributeValue.builder().s("1").build()
                )))
                .lastEvaluatedKey(lastKey)
                .count(1)
                .build();

        when(mockClient.query(any(QueryRequest.class))).thenReturn(page1);

        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class);

        CrossEntityResultWithPagination result = builder.executeWithPagination();
        assertThat(result.getResult().get(UserEntity.class)).hasSize(1);
        assertThat(result.getLastEvaluatedKey()).isEqualTo(lastKey);
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    void executeAll_iteratesMultiplePages() {
        Map<String, AttributeValue> lastKey = Map.of("pk", AttributeValue.builder().s("USER#1").build());
        QueryResponse page1 = QueryResponse.builder()
                .items(List.of(Map.of(
                        "_type", AttributeValue.builder().s("USER").build(),
                        "pk", AttributeValue.builder().s("USER#1").build(),
                        "userId", AttributeValue.builder().s("1").build()
                )))
                .lastEvaluatedKey(lastKey)
                .count(1)
                .build();

        QueryResponse page2 = QueryResponse.builder()
                .items(List.of(Map.of(
                        "_type", AttributeValue.builder().s("USER").build(),
                        "pk", AttributeValue.builder().s("USER#2").build(),
                        "userId", AttributeValue.builder().s("2").build()
                )))
                .lastEvaluatedKey(Map.of())
                .count(1)
                .build();

        when(mockClient.query(any(QueryRequest.class)))
                .thenReturn(page1, page2);

        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class);

        List<CrossEntityResult> pages = builder.executeAll();
        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).get(UserEntity.class)).hasSize(1);
        assertThat(pages.get(1).get(UserEntity.class)).hasSize(1);
        verify(mockClient, times(2)).query(any(QueryRequest.class));
    }

    @Test
    void executeAndGetFirst_returnsFirstPageOnly() {
        Map<String, AttributeValue> lastKey = Map.of("pk", AttributeValue.builder().s("USER#1").build());
        QueryResponse page1 = QueryResponse.builder()
                .items(List.of(Map.of(
                        "_type", AttributeValue.builder().s("USER").build(),
                        "pk", AttributeValue.builder().s("USER#1").build(),
                        "userId", AttributeValue.builder().s("1").build()
                )))
                .lastEvaluatedKey(lastKey)
                .count(1)
                .build();

        when(mockClient.query(any(QueryRequest.class))).thenReturn(page1);

        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class);

        var result = builder.executeAndGetFirst();
        assertThat(result).isPresent();
        assertThat(result.get().get(UserEntity.class)).hasSize(1);
    }

    @Test
    void executeAndGetFirst_withNoResults_returnsEmpty() {
        QueryResponse emptyPage = QueryResponse.builder()
                .items(List.of())
                .count(0)
                .build();

        when(mockClient.query(any(QueryRequest.class))).thenReturn(emptyPage);

        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class);

        var result = builder.executeAndGetFirst();
        assertThat(result).isEmpty();
    }

    @Test
    void count_returnsCorrectCountFromSelectCount() {
        Map<String, AttributeValue> lastKey = Map.of("pk", AttributeValue.builder().s("next").build());
        QueryResponse page1 = QueryResponse.builder()
                .items(List.of())
                .count(5)
                .lastEvaluatedKey(lastKey)
                .build();

        QueryResponse page2 = QueryResponse.builder()
                .items(List.of())
                .count(3)
                .build();

        when(mockClient.query(any(QueryRequest.class)))
                .thenReturn(page1, page2);

        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class);

        long count = builder.count();
        assertThat(count).isEqualTo(8);
        verify(mockClient, times(2)).query(any(QueryRequest.class));
    }

    // ============ Projection Tests ============

    @Test
    void project_addsProjectionExpressionToRequest() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class)
                .project("userId", "email");

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.projectionExpression()).isEqualTo("userId, email");
    }

    // ============ ConsistentRead Tests ============

    @Test
    void consistentRead_addsConsistentReadToRequest() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class)
                .consistentRead(true);

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.consistentRead()).isTrue();
    }

    @Test
    void consistentRead_false_setsConsistentReadToFalse() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class)
                .consistentRead(false);

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.consistentRead()).isFalse();
    }

    // ============ ScanIndexForward Tests ============

    @Test
    void scanIndexForward_setsScanIndexForwardParameter() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class)
                .scanIndexForward(false);

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.scanIndexForward()).isFalse();
    }

    @Test
    void scanIndexForward_true_setsScanIndexForwardToTrue() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class)
                .scanIndexForward(true);

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.scanIndexForward()).isTrue();
    }

    // ============ PK Attribute Name Test ============

    @Test
    void execute_usesCorrectPkAttributeNameFromAnnotation() {
        builder.partitionKey("USER#abc123")
                .includeEntity(UserEntity.class);

        QueryResponse response = QueryResponse.builder()
                .items(List.of(Map.of(
                        "_type", AttributeValue.builder().s("USER").build(),
                        "pk", AttributeValue.builder().s("USER#abc123").build(),
                        "userId", AttributeValue.builder().s("abc123").build()
                )))
                .count(1)
                .build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        // UserEntity has @KeyComponent(component="PK") on getUserId() -> attributeName = "userId"
        assertThat(request.expressionAttributeNames()).containsEntry("#pk", "userId");
    }

    // ============ Pagination with empty included entities ============

    @Test
    void executeWithPagination_withNoIncludedEntities_returnsEmpty() {
        builder.partitionKey("ANY");
        CrossEntityResultWithPagination result = builder.executeWithPagination();
        assertThat(result.getResult().isEmpty()).isTrue();
        assertThat(result.hasMore()).isFalse();
        verify(mockClient, never()).query(any(QueryRequest.class));
    }

    @Test
    void executeAll_withNoIncludedEntities_returnsEmptyList() {
        builder.partitionKey("ANY");
        List<CrossEntityResult> pages = builder.executeAll();
        assertThat(pages).hasSize(1);
        assertThat(pages.getFirst().isEmpty()).isTrue();
        verify(mockClient, never()).query(any(QueryRequest.class));
    }

    // ============ No partition key throws ============

    @Test
    void executeWithPagination_withoutPartitionKey_shouldThrow() {
        assertThrows(IllegalStateException.class, () -> builder.executeWithPagination());
    }

    @Test
    void executeAll_withoutPartitionKey_shouldThrow() {
        assertThrows(IllegalStateException.class, () -> builder.executeAll());
    }

    @Test
    void count_withoutPartitionKey_shouldThrow() {
        assertThrows(IllegalStateException.class, () -> builder.count());
    }

    // ============ Additional Branch Coverage Tests ============

    @Test
    @DisplayName("sort key condition with entity missing SK attribute skips sort key expression")
    void sortKeyCondition_withEntityWithoutSk_skipsSortKeyExpression() {
        // UserEntity has no SK attribute; sort key condition should be silently skipped
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class)
                .sortKeyBeginsWith("IGNORED");

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        // Only the PK condition should be present, no sort key condition
        assertThat(request.keyConditionExpression()).isEqualTo("#pk = :pk");
    }

    @Test
    @DisplayName("limit sets limit parameter on QueryRequest")
    void limit_setsLimitOnQueryRequest() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class)
                .limit(10);

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.limit()).isEqualTo(10);
    }

    @Test
    @DisplayName("project with empty array omits projection expression")
    void project_withEmptyArray_omitsProjectionExpression() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class)
                .project(); // empty varargs

        QueryResponse response = QueryResponse.builder().items(List.of()).count(0).build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        builder.execute();

        verify(mockClient).query(requestCaptor.capture());
        QueryRequest request = requestCaptor.getValue();
        assertThat(request.projectionExpression()).isNull();
    }

    @Test
    @DisplayName("execute skips items with missing discriminator attribute")
    void execute_withItemMissingDiscriminator_skipsItem() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class);

        // Item has no "_type" attribute
        QueryResponse response = QueryResponse.builder()
                .items(List.of(Map.of(
                        "pk", AttributeValue.builder().s("USER#abc").build(),
                        "userId", AttributeValue.builder().s("abc").build()
                )))
                .count(1)
                .build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        CrossEntityResult result = builder.execute();
        // Item with missing discriminator should be skipped
        assertThat(result.get(UserEntity.class)).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("execute skips items with unknown discriminator value")
    void execute_withItemHavingUnknownDiscriminator_skipsItem() {
        builder.partitionKey("PART#abc")
                .includeEntity(UserEntity.class);

        // Item has discriminator "UNKNOWN" that doesn't match any included schema
        QueryResponse response = QueryResponse.builder()
                .items(List.of(Map.of(
                        "_type", AttributeValue.builder().s("UNKNOWN").build(),
                        "pk", AttributeValue.builder().s("PART#abc").build()
                )))
                .count(1)
                .build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(response);

        CrossEntityResult result = builder.execute();
        assertThat(result.get(UserEntity.class)).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("executeQuery wraps DynamoDbException in OperationFailedException")
    void executeQuery_wrapsDynamoDbException() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class);

        DynamoDbException dde = mock(DynamoDbException.class);
        when(mockClient.query(any(QueryRequest.class))).thenThrow(dde);

        assertThrows(OperationFailedException.class, () -> builder.execute());
    }

    @Test
    @DisplayName("count with no included entities returns zero")
    void count_withNoIncludedEntities_returnsZero() {
        builder.partitionKey("ANY");
        long result = builder.count();
        assertThat(result).isZero();
        verify(mockClient, never()).query(any(QueryRequest.class));
    }

    @Test
    @DisplayName("executeAll with empty result page returns single empty page")
    void executeAll_withEmptyResult_returnsSingleEmptyPage() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class);

        QueryResponse emptyPage = QueryResponse.builder()
                .items(List.of())
                .count(0)
                .build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(emptyPage);

        List<CrossEntityResult> pages = builder.executeAll();
        assertThat(pages).hasSize(1);
        assertThat(pages.getFirst().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("executeWithPagination with empty result returns empty page with no lastKey")
    void executeWithPagination_withEmptyResult_returnsEmptyPage() {
        builder.partitionKey("USER#abc")
                .includeEntity(UserEntity.class);

        QueryResponse emptyPage = QueryResponse.builder()
                .items(List.of())
                .count(0)
                .build();
        when(mockClient.query(any(QueryRequest.class))).thenReturn(emptyPage);

        CrossEntityResultWithPagination page = builder.executeWithPagination();
        assertThat(page.getResult().isEmpty()).isTrue();
        assertThat(page.hasMore()).isFalse();
    }

    // --- Test entity classes ---

    public static class BaseEntityWithPk {
        protected String pk;

        @DynamoDbPartitionKey
        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }
    }

    @DynamoDbBean
    @Entity(discriminator = "USER", table = "myapp")
    public static class UserEntity extends BaseEntityWithPk {
        private String userId;

        @SuppressWarnings("PMD.CallSuperInConstructor")
        public UserEntity() {
            // default constructor required by DynamoDbBean
        }

        @KeyComponent(component = "PK")
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }

    @DynamoDbBean
    @Entity(discriminator = "POST", table = "myapp")
    public static class PostEntity extends BaseEntityWithPk {
        private String postId;

        @SuppressWarnings("PMD.CallSuperInConstructor")
        public PostEntity() {
            // default constructor required by DynamoDbBean
        }

        @KeyComponent(component = "PK")
        public String getPostId() {
            return postId;
        }

        public void setPostId(String postId) {
            this.postId = postId;
        }
    }

    @DynamoDbBean
    @Entity(discriminator = "ITEM", table = "myapp")
    public static class EntityWithSk extends BaseEntityWithPk {
        private String sk;

        @SuppressWarnings("PMD.CallSuperInConstructor")
        public EntityWithSk() {
            // default constructor required by DynamoDbBean
        }

        @KeyComponent(component = "PK")
        public String getPkComponent() {
            return pk;
        }

        public void setPkComponent(String pk) {
            this.pk = pk;
        }

        @KeyComponent(component = "SK")
        public String getSk() {
            return sk;
        }

        public void setSk(String sk) {
            this.sk = sk;
        }
    }
}
