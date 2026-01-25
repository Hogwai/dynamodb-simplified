package com.hogwai.app.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Serdeable
public class PostSearchRequest {
    private String subreddit;
    private String author;
    private String keyword;
    private Long sinceUtc;
    private Long untilUtc;
    private String titleContains;
    private Integer minKeywords;
    private Integer limit;
}
