package com.hogwai.app.repository;

import com.hogwai.app.model.SocialMediaPost;
import com.hogwai.app.search.PostSearchCriteria;
import com.hogwai.dynamodb.simplified.DynamoSimplifiedClient;
import com.hogwai.dynamodb.simplified.DynamoSimplifiedClient.TableOperations;
import com.hogwai.dynamodb.simplified.expression.FilterExpression;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class PostRepository {

    private static final String TABLE_NAME = "posts";
    public static final String AUTHOR = "author";
    public static final String CREATED_UTC = "createdUtc";
    public static final String TITLE = "title";

    private final TableOperations<SocialMediaPost> table;

    public PostRepository(DynamoDbClient dynamoDbClient) {
        this.table = DynamoSimplifiedClient.create(dynamoDbClient)
                                           .table(TABLE_NAME, SocialMediaPost.class);
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
        return table.get(subreddit, id);
    }

    public SocialMediaPost update(SocialMediaPost socialMediaPost) {
        return table.updateItem(socialMediaPost);
    }

    public void delete(String subreddit, String id) {
        table.deleteItem(subreddit, id);
    }

    // ============ Queries par subreddit ============

    public List<SocialMediaPost> findBySubreddit(String subreddit) {
        return table.query()
                    .partitionKey(subreddit)
                    .execute();
    }

    public List<SocialMediaPost> findBySubreddit(String subreddit, int limit) {
        return table.query()
                    .partitionKey(subreddit)
                    .descending()
                    .limit(limit)
                    .execute();
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
                    .execute();
    }

    public List<SocialMediaPost> findByIdBetween(String subreddit, String startId, String endId) {
        return table.query()
                    .partitionKeyAndSortKeyBetween(subreddit, startId, endId)
                    .execute();
    }

    // ============ Queries par auteur ============

    public List<SocialMediaPost> findByAuthor(String subreddit, String author) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.eq(AUTHOR, author))
                    .execute();
    }

    public List<SocialMediaPost> findByAuthors(String subreddit, List<String> authors) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.in(AUTHOR, authors.toArray()))
                    .execute();
    }

    // ============ Queries temporelles ============

    public List<SocialMediaPost> findCreatedAfter(String subreddit, long timestampUtc) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.gt(CREATED_UTC, timestampUtc))
                    .descending()
                    .execute();
    }

    public List<SocialMediaPost> findCreatedBefore(String subreddit, long timestampUtc) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.lt(CREATED_UTC, timestampUtc))
                    .descending()
                    .execute();
    }

    public List<SocialMediaPost> findCreatedBetween(String subreddit, long startUtc, long endUtc) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.between(CREATED_UTC, startUtc, endUtc))
                    .descending()
                    .execute();
    }

    // ============ Queries par keywords ============

    public List<SocialMediaPost> findByKeyword(String subreddit, String keyword) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.contains("keywords", keyword))
                    .execute();
    }

    public List<SocialMediaPost> findWithMinKeywords(String subreddit, int minCount) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.sizeGe("keywords", minCount))
                    .execute();
    }

    public List<SocialMediaPost> findWithKeywordCount(String subreddit, int min, int max) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.sizeBetween("keywords", min, max))
                    .execute();
    }

    // ============ Recherche textuelle ============

    public List<SocialMediaPost> searchByTitle(String subreddit, String text) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.contains(TITLE, text))
                    .execute();
    }

    public List<SocialMediaPost> searchByTitlePrefix(String subreddit, String prefix) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f.beginsWith(TITLE, prefix))
                    .execute();
    }

    public List<SocialMediaPost> findWithContent(String subreddit) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .exists("selfText")
                            .and()
                            .sizeGt("selfText", 0))
                    .execute();
    }

    // ============ Queries combinées ============

    public List<SocialMediaPost> findRecentByAuthor(String subreddit, String author, long sinceUtc) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .eq(AUTHOR, author)
                            .and()
                            .gt(CREATED_UTC, sinceUtc))
                    .descending()
                    .execute();
    }

    public List<SocialMediaPost> findRecentWithKeyword(String subreddit, String keyword, long sinceUtc, int limit) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .contains("keywords", keyword)
                            .and()
                            .gt(CREATED_UTC, sinceUtc))
                    .descending()
                    .limit(limit)
                    .execute();
    }

    public List<SocialMediaPost> findByAuthorWithKeywords(String subreddit, String author, int minKeywords) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .eq(AUTHOR, author)
                            .and()
                            .sizeGe("keywords", minKeywords))
                    .execute();
    }

    // ============ Queries avec OR ============

    public List<SocialMediaPost> findByEitherAuthor(String subreddit, String author1, String author2) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .eq(AUTHOR, author1)
                            .or()
                            .eq(AUTHOR, author2))
                    .execute();
    }

    public List<SocialMediaPost> findByAuthorOrKeyword(String subreddit, String author, String keyword) {
        return table.query()
                    .partitionKey(subreddit)
                    .filter(f -> f
                            .group(FilterExpression.builder()
                                                   .eq(AUTHOR, author))
                            .or()
                            .group(FilterExpression.builder()
                                                   .contains("keywords", keyword)))
                    .execute();
    }

    // ============ Projections ============

    public List<SocialMediaPost> findSummaries(String subreddit, int limit) {
        return table.query()
                    .partitionKey(subreddit)
                    .project("id", "title", AUTHOR, CREATED_UTC)
                    .descending()
                    .limit(limit)
                    .execute();
    }

    public List<SocialMediaPost> findTitlesOnly(String subreddit) {
        return table.query()
                    .partitionKey(subreddit)
                    .project("id", "title")
                    .execute();
    }

    // ============ Scans (cross-subreddit) ============

    public List<SocialMediaPost> findAllByAuthor(String author) {
        return table.scan()
                    .filter(f -> f.eq(AUTHOR, author))
                    .execute();
    }

    public List<SocialMediaPost> findAllWithKeyword(String keyword) {
        return table.scan()
                    .filter(f -> f.contains("keywords", keyword))
                    .execute();
    }

    public List<SocialMediaPost> findAllCreatedAfter(long timestampUtc) {
        return table.scan()
                    .filter(f -> f.gt(CREATED_UTC, timestampUtc))
                    .execute();
    }

    public PagedResult<SocialMediaPost> scanAllPaginated(int pageSize, Map<String, AttributeValue> lastKey) {
        var scan = table.scan().limit(pageSize);

        if (lastKey != null && !lastKey.isEmpty()) {
            scan.startFrom(lastKey);
        }

        return scan.executeWithPagination();
    }

    // ============ Opérations conditionnelles ============

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

    public SocialMediaPost updateIfAuthorMatches(SocialMediaPost socialMediaPost, String expectedAuthor) {
        return table.update(socialMediaPost)
                    .condition(c -> c.eq(AUTHOR, expectedAuthor))
                    .execute();
    }

    public SocialMediaPost deleteIfOlderThan(String subreddit, String id, long olderThanUtc) {
        return table.delete(subreddit, id)
                    .condition(c -> c.lt(CREATED_UTC, olderThanUtc))
                    .execute();
    }

    public SocialMediaPost deleteByAuthor(String subreddit, String id, String author) {
        return table.delete(subreddit, id)
                    .condition(c -> c.eq(AUTHOR, author))
                    .execute();
    }

    // ============ Méthode de recherche dynamique ============

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

        return query.execute();
    }

    private FilterExpression buildFilter(FilterExpression f, PostSearchCriteria criteria) {
        boolean first = true;

        if (criteria.getAuthor() != null) {
            f.eq(AUTHOR, criteria.getAuthor());
            first = false;
        }

        if (criteria.getSinceUtc() != null) {
            if (!first) f.and();
            f.gt(CREATED_UTC, criteria.getSinceUtc());
            first = false;
        }

        if (criteria.getUntilUtc() != null) {
            if (!first) f.and();
            f.lt(CREATED_UTC, criteria.getUntilUtc());
            first = false;
        }

        if (criteria.getKeyword() != null) {
            if (!first) f.and();
            f.contains("keywords", criteria.getKeyword());
            first = false;
        }

        if (criteria.getMinKeywords() != null) {
            if (!first) f.and();
            f.sizeGe("keywords", criteria.getMinKeywords());
            first = false;
        }

        if (criteria.getTitleContains() != null) {
            if (!first) f.and();
            f.contains("title", criteria.getTitleContains());
        }

        return f;
    }
}