package dev.hogwai.app.controller;

import dev.hogwai.app.dto.request.CreatePostRequest;
import dev.hogwai.app.dto.request.PostSearchRequest;
import dev.hogwai.app.dto.response.PagedResponse;
import dev.hogwai.app.service.PostService;
import dev.hogwai.app.model.SocialMediaPost;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Get("/{subreddit}")
    public List<SocialMediaPost> listPosts(@PathVariable String subreddit,
                                           @QueryValue Integer limit) {
        return postService.getRecentPosts(subreddit, limit != null ? limit : 50);
    }

    @Get("/{subreddit}/{id}")
    public Optional<SocialMediaPost> getPost(@PathVariable String subreddit,
                                             @PathVariable String id) {
        return postService.getPost(subreddit, id);
    }

    @Get("/{subreddit}/author/{author}")
    public List<SocialMediaPost> getByAuthor(@PathVariable String subreddit,
                                             @PathVariable String author) {
        return postService.getPostsByAuthor(subreddit, author);
    }

    @Get("/{subreddit}/recent")
    public List<SocialMediaPost> getRecentPosts(@PathVariable String subreddit,
                                                @QueryValue Integer hours) {
        return postService.getPostsLastHours(subreddit, hours);
    }

    @Get("/{subreddit}/search")
    public List<SocialMediaPost> search(@PathVariable String subreddit,
                                        @QueryValue String author,
                                        @QueryValue String keyword,
                                        @QueryValue Long since,
                                        @QueryValue Integer limit) {

        PostSearchRequest request = PostSearchRequest.builder()
                                                     .subreddit(subreddit)
                                                     .author(author)
                                                     .keyword(keyword)
                                                     .sinceUtc(since)
                                                     .limit(limit)
                                                     .build();

        return postService.search(request);
    }

    @Get("/{subreddit}/paginated")
    public PagedResponse<SocialMediaPost> listPostsPaginated(@PathVariable String subreddit,
                                                             @QueryValue Integer pageSize,
                                                             @QueryValue String cursor) {
        return postService.getPostsPaginated(subreddit, pageSize, cursor);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public SocialMediaPost createPost(@Body CreatePostRequest request) {
        return postService.createPost(request);
    }

    @Put("/{subreddit}/{id}")
    public SocialMediaPost updatePost(@PathVariable String subreddit,
                                      @PathVariable String id,
                                      @Body SocialMediaPost socialMediaPost) {
        return postService.updatePost(subreddit, id, socialMediaPost);
    }

    @Delete("/{subreddit}/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable String subreddit,
                           @PathVariable String id) {
        postService.deletePost(subreddit, id);
    }
}