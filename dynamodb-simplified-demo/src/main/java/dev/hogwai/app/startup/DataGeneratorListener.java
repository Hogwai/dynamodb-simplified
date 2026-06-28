package dev.hogwai.app.startup;

import dev.hogwai.app.model.SocialMediaPost;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import jakarta.inject.Singleton;
import net.datafaker.Faker;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

@Singleton
@Requires(property = "app.data-generator.enabled", value = "true")
public class DataGeneratorListener implements ApplicationEventListener<ApplicationStartupEvent> {

    private final DynamoDbEnhancedClient enhancedClient;
    private final Faker faker = new Faker();

    @Value("${app.data-generator.count:1000}")
    private int count;

    @Value("${app.data-generator.table:posts}")
    private String tableName;

    private static final List<String> SUBREDDITS = List.of(
            "java", "programming", "aws", "devops", "kotlin",
            "spring", "micronaut", "docker", "kubernetes", "linux"
    );

    private static final List<String> KEYWORDS_POOL = List.of(
            "tutorial", "help", "question", "discussion", "news",
            "library", "framework", "performance", "security", "database"
    );

    public DataGeneratorListener(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
    }

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        DynamoDbTable<SocialMediaPost> table = enhancedClient.table(tableName, TableSchema.fromBean(SocialMediaPost.class));

        List<SocialMediaPost> socialMediaPosts = IntStream.range(0, count)
                                                          .mapToObj(i -> generatePost())
                                                          .toList();

        List<List<SocialMediaPost>> batches = partition(socialMediaPosts, 25);

        for (List<SocialMediaPost> batch : batches) {
            writeBatch(table, batch);
        }
    }

    private SocialMediaPost generatePost() {
        String subreddit = SUBREDDITS.get(faker.random().nextInt(SUBREDDITS.size()));

        long createdUtc = Instant.now()
                                 .minus(faker.random().nextInt(730), ChronoUnit.DAYS)
                                 .minus(faker.random().nextInt(86400), ChronoUnit.SECONDS)
                                 .getEpochSecond();

        Set<String> keywords = new HashSet<>();
        while (keywords.size() < faker.random().nextInt(2, 5)) {
            keywords.add(KEYWORDS_POOL.get(faker.random().nextInt(KEYWORDS_POOL.size())));
        }

        return new SocialMediaPost(
                UUID.randomUUID().toString(),
                subreddit,
                createdUtc,
                faker.internet().uuid(),
                faker.lorem().sentence(faker.random().nextInt(5, 15)),
                String.join("\n\n", faker.lorem().paragraphs(faker.random().nextInt(1, 5))),
                "/r/" + subreddit + "/comments/" + faker.random().nextInt(1, 5),
                keywords
        );
    }

    private void writeBatch(DynamoDbTable<SocialMediaPost> table, List<SocialMediaPost> socialMediaPosts) {
        WriteBatch.Builder<SocialMediaPost> builder = WriteBatch.builder(SocialMediaPost.class)
                                                                .mappedTableResource(table);
        socialMediaPosts.forEach(builder::addPutItem);

        enhancedClient.batchWriteItem(BatchWriteItemEnhancedRequest.builder()
                                                                   .addWriteBatch(builder.build())
                                                                   .build());
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}