package dev.hogwai.app.dto.response;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Serdeable
public class PagedResponse<T> {
    private List<T> items;
    private String nextCursor;
    private boolean hasMore;
}
