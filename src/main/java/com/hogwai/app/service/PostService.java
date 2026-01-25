package com.hogwai.app.service;

import com.hogwai.app.dto.request.CreatePostRequest;
import com.hogwai.app.dto.request.PostSearchRequest;
import com.hogwai.app.dto.response.PagedResponse;
import com.hogwai.app.exception.PostNotFoundException;
import com.hogwai.app.model.SocialMediaPost;
import com.hogwai.app.repository.PostRepository;
import com.hogwai.app.search.PostSearchCriteria;
import jakarta.inject.Singleton;
import com.hogwai.dynamodb.simplified.result.PagedResult;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Singleton
public class PostService {

    private final PostRepository repository;

    public PostService(PostRepository repository) {
        this.repository = repository;
    }

    // Créer un post
    public void createPost(String subreddit,
                           String title,
                           String author,
                           String content,
                           Set<String> keywords) {
        SocialMediaPost socialMediaPost = new SocialMediaPost(
                generateId(),
                subreddit,
                Instant.now().getEpochSecond(),
                author,
                title,
                content,
                "/r/" + subreddit + "/comments/" + generateId(),
                keywords
        );
        repository.saveIfNotExists(socialMediaPost);
    }

    // Posts récents d'un subreddit
    public List<SocialMediaPost> getRecentPosts(String subreddit, int limit) {
        return repository.findBySubreddit(subreddit, limit);
    }

    // Posts des dernières 24h
    public List<SocialMediaPost> getPostsLast24Hours(String subreddit) {
        long yesterday = Instant.now().minus(24, ChronoUnit.HOURS).getEpochSecond();
        return repository.findCreatedAfter(subreddit, yesterday);
    }

    // Recherche avancée
    public List<SocialMediaPost> searchPosts(String subreddit, String author, String keyword, int limit) {
        PostSearchCriteria criteria = PostSearchCriteria.builder()
                                                        .subreddit(subreddit)
                                                        .author(author)
                                                        .keyword(keyword)
                                                        .sinceUtc(Instant.now().minus(7, ChronoUnit.DAYS).getEpochSecond())
                                                        .limit(limit)
                                                        .build();

        return repository.search(criteria);
    }

    // Pagination
    public PagedResult<SocialMediaPost> getPostsPage(String subreddit, int pageSize, String lastKeyJson) {
        var lastKey = lastKeyJson != null ? deserializeKey(lastKeyJson) : null;
        return repository.findBySubredditPaginated(subreddit, pageSize, lastKey);
    }

    private String generateId() {
        return java.util.UUID.randomUUID().toString();
    }

    private java.util.Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>
    deserializeKey(String json) {
        // Implémentation de désérialisation
        return null;
    }

    // ============ Lecture ============

    public Optional<SocialMediaPost> getPost(String subreddit, String id) {
        return repository.findById(subreddit, id);
    }

    public List<SocialMediaPost> getPostsByAuthor(String subreddit, String author) {
        return repository.findByAuthor(subreddit, author);
    }

    public List<SocialMediaPost> getPostsLastHours(String subreddit, int hours) {
        long since = Instant.now().minus(hours, ChronoUnit.HOURS).getEpochSecond();
        return repository.findCreatedAfter(subreddit, since);
    }

    public List<SocialMediaPost> getPostsByKeyword(String subreddit, String keyword) {
        return repository.findByKeyword(subreddit, keyword);
    }

    // ============ Recherche ============

    public List<SocialMediaPost> search(PostSearchRequest request) {
        PostSearchCriteria criteria = PostSearchCriteria.builder()
                                                        .subreddit(request.getSubreddit())
                                                        .author(request.getAuthor())
                                                        .keyword(request.getKeyword())
                                                        .sinceUtc(request.getSinceUtc())
                                                        .untilUtc(request.getUntilUtc())
                                                        .titleContains(request.getTitleContains())
                                                        .minKeywords(request.getMinKeywords())
                                                        .limit(request.getLimit())
                                                        .build();

        return repository.search(criteria);
    }

    // ============ Pagination ============

    public PagedResponse<SocialMediaPost> getPostsPaginated(String subreddit, int pageSize, String cursor) {
        Map<String, AttributeValue> lastKey = decodeCursor(cursor);

        PagedResult<SocialMediaPost> result = repository.findBySubredditPaginated(subreddit, pageSize, lastKey);

        return PagedResponse.<SocialMediaPost>builder()
                            .items(result.getItems())
                            .nextCursor(encodeCursor(result.getLastEvaluatedKey()))
                            .hasMore(result.hasMorePages())
                            .build();
    }

    // ============ Création ============

    public SocialMediaPost createPost(CreatePostRequest request) {
        SocialMediaPost socialMediaPost = SocialMediaPost.builder()
                                                         .id(generateId())
                                                         .subreddit(request.getSubreddit())
                                                         .author(request.getAuthor())
                                                         .title(request.getTitle())
                                                         .selfText(request.getSelfText())
                                                         .keywords(request.getKeywords())
                                                         .createdUtc(Instant.now().getEpochSecond())
                                                         .permalink(buildPermalink(request.getSubreddit()))
                                                         .build();

        repository.saveIfNotExists(socialMediaPost);
        return socialMediaPost;
    }

    // ============ Mise à jour ============

    public SocialMediaPost updatePost(String subreddit, String id, SocialMediaPost updatedSocialMediaPost) {
        // Vérifier que le post existe
        SocialMediaPost existing = repository.findById(subreddit, id)
                                             .orElseThrow(() -> new PostNotFoundException(subreddit, id));

        // Garder les champs immuables
        updatedSocialMediaPost.setSubreddit(subreddit);
        updatedSocialMediaPost.setId(id);
        updatedSocialMediaPost.setCreatedUtc(existing.getCreatedUtc());
        updatedSocialMediaPost.setAuthor(existing.getAuthor());

        return repository.update(updatedSocialMediaPost);
    }

    // ============ Suppression ============

    public void deletePost(String subreddit, String id, String requestingUserId) {
        // Vérifier que le post existe
        SocialMediaPost socialMediaPost = repository.findById(subreddit, id)
                                                    .orElseThrow(() -> new PostNotFoundException(subreddit, id));

        repository.delete(subreddit, id);
    }

    public boolean tryDeleteByAuthor(String subreddit, String id, String author) {
        try {
            repository.deleteByAuthor(subreddit, id, author);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    // ============ Méthodes utilitaires ============

    private String buildPermalink(String subreddit) {
        return "/r/%s/comments/%s".formatted(subreddit, generateId());
    }

    private String encodeCursor(Map<String, AttributeValue> lastKey) {
        if (lastKey == null || lastKey.isEmpty()) {
            return null;
        }
        // Encoder en Base64 pour le transport
        try {
            StringBuilder sb = new StringBuilder();
            lastKey.forEach((k, v) -> {
                if (!sb.isEmpty()) sb.append("|");
                sb.append(k).append("=").append(v.s() != null ? v.s() : v.n());
            });
            return Base64.getUrlEncoder().encodeToString(sb.toString().getBytes());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, AttributeValue> decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            Map<String, AttributeValue> result = new HashMap<>();
            for (String part : decoded.split("\\|")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    result.put(kv[0], AttributeValue.builder().s(kv[1]).build());
                }
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
