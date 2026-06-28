package dev.hogwai.app.repository;

import dev.hogwai.app.model.SocialMediaPost;
import dev.hogwai.app.search.PostSearchCriteria;
import dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.Table;
import dev.hogwai.dynamodb.simplified.expression.FilterExpression;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class PostRepository implements AutoCloseable {

    private static final String TABLE_NAME = "posts";
    public static final String AUTHOR = "author";
    public static final String CREATED_UTC = "createdUtc";
    public static final String TITLE = "title";
    public static final String KEYWORDS = "keywords";

    private final Table<SocialMediaPost> table;
    private final DynamoSimplifiedClient dynamoSimplifiedClient;

    public PostRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoSimplifiedClient = DynamoSimplifiedClient.create(dynamoDbClient);
        this.table = this.dynamoSimplifiedClient.table(TABLE_NAME, SocialMediaPost.class);
    }

    @Override
    public void close() {
        dynamoSimplifiedClient.close();
    }

    // ============ CRUD de base ============

    public void save(SocialMediaPost socialMediaPost) {
        table.putItem(socialMediaPost);
    }

    public void saveIfNotExists(SocialMediaPost socialMediaPost) {
        table.put(socialMediaPost)
             .onlyIfNotExists("id")
             .execute();
    }

    public Optional<SocialMediaPost> findById(String subreddit, String id) {
        return table.getItem(subreddit, id);
    }

    public SocialMediaPost update(SocialMediaPost socialMediaPost) {
        return table.updateItem(socialMediaPost);
    }

    public void delete(String subreddit, String id) {
        table.deleteItem(subreddit, id);
    }

    // ============ Queries by Subreddit ============

    public List<SocialMediaPost> findBySubreddit(String subreddit) {
        return table.query()
                    .partitionKey(subreddit)
                    .executeAll();
    }

    public List<SocialMediaPost> findBySubreddit(String subreddit, int limit) {
        return table.query()
                    .partitionKey(subreddit)
                    .descending()
                    .limit(limit)
                    .executeAll();
    }

    public PagedResult<SocialMediaPost> findBySubredditPaginated(String subreddit,
                                                                 int pageSize,
                                                                 Map<String, AttributeValue> lastKey) {
        var query = table.query()
                         .partitionKey(subreddit)
                         .descending()
                         .limit(pageSize);

        if (lastKey != null && !lastKey.isEmpty()) {
            query.startFrom(lastKey);
        }

        return query.executeWithPagination();
    }

    // ============ Queries par ID ============

    public List<SocialMediaPost> findByIdPrefix(String subreddit, String idPrefix) {
        return table.query()
                    .partitionKeyAndSortKeyBeginsWith(subreddit, idPrefix)
                    .executeAll();
    }

    public List<SocialMediaPost> findByIdBetween(String subreddit, String startId, String endId) {
        return table.query()
                    .partitionKeyAndSortKeyBetween(subreddit, startId, endId)
                    .executeAll();
    }

    // ============ Queries par auteur ============

    public List<SocialMediaPost> findByAuthor(String subreddit, String author) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.eq(AUTHOR, author))
                    .executeAll();
    }

    public List<SocialMediaPost> findByAuthors(String subreddit, List<String> authors) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.in(AUTHOR, authors.toArray()))
                    .executeAll();
    }

    // ============ Queries temporelles ============

    public List<SocialMediaPost> findCreatedAfter(String subreddit, long timestampUtc) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.gt(CREATED_UTC, timestampUtc))
                    .descending()
                    .executeAll();
    }

    public List<SocialMediaPost> findCreatedBefore(String subreddit, long timestampUtc) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.lt(CREATED_UTC, timestampUtc))
                    .descending()
                    .executeAll();
    }

    public List<SocialMediaPost> findCreatedBetween(String subreddit, long startUtc, long endUtc) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.between(CREATED_UTC, startUtc, endUtc))
                    .descending()
                    .executeAll();
    }

    // ============ Queries par keywords ============

    public List<SocialMediaPost> findByKeyword(String subreddit, String keyword) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.contains(KEYWORDS, keyword))
                    .executeAll();
    }

    public List<SocialMediaPost> findWithMinKeywords(String subreddit, int minCount) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.sizeGe(KEYWORDS, minCount))
                    .executeAll();
    }

    public List<SocialMediaPost> findWithKeywordCount(String subreddit, int min, int max) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.sizeBetween(KEYWORDS, min, max))
                    .executeAll();
    }

    // ============ Recherche textuelle ============

    public List<SocialMediaPost> searchByTitle(String subreddit, String text) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.contains(TITLE, text))
                    .executeAll();
    }

    public List<SocialMediaPost> searchByTitlePrefix(String subreddit, String prefix) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.beginsWith(TITLE, prefix))
                    .executeAll();
    }

    public List<SocialMediaPost> findWithContent(String subreddit) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .exists("selfText")
                            .and()
                            .sizeGt("selfText", 0))
                    .executeAll();
    }

    // ============ Combined Queries ============

    public List<SocialMediaPost> findRecentByAuthor(String subreddit, String author, long sinceUtc) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .eq(AUTHOR, author)
                            .and()
                            .gt(CREATED_UTC, sinceUtc))
                    .descending()
                    .executeAll();
    }

    public List<SocialMediaPost> findRecentWithKeyword(String subreddit, String keyword, long sinceUtc, int limit) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .contains(KEYWORDS, keyword)
                            .and()
                            .gt(CREATED_UTC, sinceUtc))
                    .descending()
                    .limit(limit)
                    .executeAll();
    }

    public List<SocialMediaPost> findByAuthorWithKeywords(String subreddit, String author, int minKeywords) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .eq(AUTHOR, author)
                            .and()
                            .sizeGe(KEYWORDS, minKeywords))
                    .executeAll();
    }

    // ============ Queries avec OR ============

    public List<SocialMediaPost> findByEitherAuthor(String subreddit, String author1, String author2) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .eq(AUTHOR, author1)
                            .or()
                            .eq(AUTHOR, author2))
                    .executeAll();
    }

    public List<SocialMediaPost> findByAuthorOrKeyword(String subreddit, String author, String keyword) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .group(FilterExpression.builder()
                                                   .eq(AUTHOR, author))
                            .or()
                            .group(FilterExpression.builder()
                                                   .contains(KEYWORDS, keyword)))
                    .executeAll();
    }

    // ============ Projections ============

    public List<SocialMediaPost> findSummaries(String subreddit, int limit) {
        return table.query()
                    .partitionKey(subreddit)
                    .project("id", TITLE, AUTHOR, CREATED_UTC)
                    .descending()
                    .limit(limit)
                    .executeAll();
    }

    public List<SocialMediaPost> findTitlesOnly(String subreddit) {
        return table.query()
                    .partitionKey(subreddit)
                    .project("id", TITLE)
                    .executeAll();
    }

    // ============ Scans (cross-subreddit) ============

    public List<SocialMediaPost> findAllByAuthor(String author) {
        return table.scan()
                    .filter(f -> f.eq(AUTHOR, author))
                    .executeAll();
    }

    public List<SocialMediaPost> findAllWithKeyword(String keyword) {
        return table.scan()
                    .filter(f -> f.contains(KEYWORDS, keyword))
                    .executeAll();
    }

    public List<SocialMediaPost> findAllCreatedAfter(long timestampUtc) {
        return table.scan()
                    .filter(f -> f.gt(CREATED_UTC, timestampUtc))
                    .executeAll();
    }

    public PagedResult<SocialMediaPost> scanAllPaginated(int pageSize, Map<String, AttributeValue> lastKey) {
        var scan = table.scan().limit(pageSize);

        if (lastKey != null && !lastKey.isEmpty()) {
            scan.startFrom(lastKey);
        }

        return scan.executeWithPagination();
    }

    // ============ Conditional Operations ============

    public void saveIfNew(SocialMediaPost socialMediaPost) {
        table.put(socialMediaPost)
             .condition(c -> c.notExists("id"))
             .execute();
    }

    public void saveOrUpdateOld(SocialMediaPost socialMediaPost, long olderThanUtc) {
        table.put(socialMediaPost)
             .condition(c -> c
                     .notExists("id")
                     .or()
                     .lt(CREATED_UTC, olderThanUtc))
             .execute();
    }

    public Optional<SocialMediaPost> updateIfAuthorMatches(SocialMediaPost socialMediaPost, String expectedAuthor) {
        return table.update(socialMediaPost)
                    .condition(c -> c.eq(AUTHOR, expectedAuthor))
                    .execute();
    }

    public Optional<SocialMediaPost> deleteIfOlderThan(String subreddit, String id, long olderThanUtc) {
        return table.delete(subreddit, id)
                    .condition(c -> c.lt(CREATED_UTC, olderThanUtc))
                    .execute();
    }

    public void deleteByAuthor(String subreddit, String id, String author) {
        table.delete(subreddit, id)
                .condition(c -> c.eq(AUTHOR, author))
                .execute();
    }

    // ============ Dynamic Search Method ============

    public List<SocialMediaPost> search(PostSearchCriteria criteria) {
        var query = table.query()
                         .partitionKey(criteria.getSubreddit())
                         .descending();

        if (criteria.getLimit() != null) {
            query.limit(criteria.getLimit());
        }

        if (criteria.hasFilters()) {
            query.filter(f -> buildFilter(f, criteria));
        }

        if (criteria.getProjectedFields() != null && !criteria.getProjectedFields().isEmpty()) {
            query.project(criteria.getProjectedFields().toArray(new String[0]));
        }

        if (criteria.getLastKey() != null) {
            query.startFrom(criteria.getLastKey());
        }

        return query.executeAll();
    }

    private void buildFilter(FilterExpression f, PostSearchCriteria criteria) {
        boolean hasPrevious = false;

        if (criteria.getAuthor() != null) {
            f.eq(AUTHOR, criteria.getAuthor());
            hasPrevious = true;
        }

        hasPrevious = addGtFilter(hasPrevious, f, criteria.getSinceUtc());
        hasPrevious = addLtFilter(hasPrevious, f, criteria.getUntilUtc());
        hasPrevious = addContainsFilter(hasPrevious, f, criteria.getKeyword(), KEYWORDS);
        hasPrevious = addSizeGeFilter(hasPrevious, f, criteria.getMinKeywords());
        addContainsFilter(hasPrevious, f, criteria.getTitleContains(), TITLE);

    }

    private static boolean addGtFilter(boolean hasPrevious, FilterExpression f, Long value) {
        if (value == null) return hasPrevious;
        if (hasPrevious) f.and();
        f.gt(CREATED_UTC, value);
        return true;
    }

    private static boolean addLtFilter(boolean hasPrevious, FilterExpression f, Long value) {
        if (value == null) return hasPrevious;
        if (hasPrevious) f.and();
        f.lt(CREATED_UTC, value);
        return true;
    }

    private static boolean addContainsFilter(boolean hasPrevious, FilterExpression f, String value, String attr) {
        if (value == null) return hasPrevious;
        if (hasPrevious) f.and();
        f.contains(attr, value);
        return true;
    }

    private static boolean addSizeGeFilter(boolean hasPrevious, FilterExpression f, Integer value) {
        if (value == null) return hasPrevious;
        if (hasPrevious) f.and();
        f.sizeGe(KEYWORDS, value);
        return true;
    }
}