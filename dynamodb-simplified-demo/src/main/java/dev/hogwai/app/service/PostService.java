package dev.hogwai.app.service;

import dev.hogwai.app.dto.request.CreatePostRequest;
import dev.hogwai.app.dto.request.PostSearchRequest;
import dev.hogwai.app.dto.response.PagedResponse;
import dev.hogwai.app.exception.PostNotFoundException;
import dev.hogwai.app.model.SocialMediaPost;
import dev.hogwai.app.repository.PostRepository;
import dev.hogwai.app.search.PostSearchCriteria;
import jakarta.inject.Singleton;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Singleton
public class PostService {

    private final PostRepository repository;

    public PostService(PostRepository repository) {
        this.repository = repository;
    }

    // Recent posts from a subreddit
    public List<SocialMediaPost> getRecentPosts(String subreddit, int limit) {
        return repository.findBySubreddit(subreddit, limit);
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    @SuppressWarnings("unused")
    private Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>
    deserializeKey(String json) {
        // Deserialization implementation
        return Collections.emptyMap();
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
                            .items(result.items())
                            .nextCursor(encodeCursor(result.lastEvaluatedKey()))
                            .hasMore(result.hasMorePages())
                            .build();
    }

    // ============ Creation ============

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

    // ============ Update ============

    public SocialMediaPost updatePost(String subreddit, String id, SocialMediaPost updatedSocialMediaPost) {
        // Verify the post exists
        SocialMediaPost existing = repository.findById(subreddit, id)
                                             .orElseThrow(() -> new PostNotFoundException(subreddit, id));

        // Preserve immutable fields
        updatedSocialMediaPost.setSubreddit(subreddit);
        updatedSocialMediaPost.setId(id);
        updatedSocialMediaPost.setCreatedUtc(existing.getCreatedUtc());
        updatedSocialMediaPost.setAuthor(existing.getAuthor());

        return repository.update(updatedSocialMediaPost);
    }

    // ============ Suppression ============

    public void deletePost(String subreddit, String id) {
        repository.delete(subreddit, id);
    }

    // ============ Utility Methods ============

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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
