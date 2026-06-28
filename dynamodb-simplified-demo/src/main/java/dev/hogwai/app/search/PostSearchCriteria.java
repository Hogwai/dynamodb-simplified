package dev.hogwai.app.search;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class PostSearchCriteria {

    private String subreddit;
    private String author;
    private Long sinceUtc;
    private Long untilUtc;
    private String keyword;
    private Integer minKeywords;
    private String titleContains;
    private Integer limit;
    private List<String> projectedFields;
    private Map<String, AttributeValue> lastKey;

    public boolean hasFilters() {
        return author != null
                || sinceUtc != null
                || untilUtc != null
                || keyword != null
                || minKeywords != null
                || titleContains != null;
    }
}
