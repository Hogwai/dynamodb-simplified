package com.hogwai.app.exception;

public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(String subreddit, String id) {
        super(String.format("Post not found: subreddit=%s, id=%s", subreddit, id));
    }
}
