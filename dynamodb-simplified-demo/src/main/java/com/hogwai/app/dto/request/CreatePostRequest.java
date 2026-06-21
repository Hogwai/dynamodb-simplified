package com.hogwai.app.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Builder
@Serdeable
public class CreatePostRequest {
    private String subreddit;
    private String author;
    private String title;
    private String selfText;
    private Set<String> keywords;
}
