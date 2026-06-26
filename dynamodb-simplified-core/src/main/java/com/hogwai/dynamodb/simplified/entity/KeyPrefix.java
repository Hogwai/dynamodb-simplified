package com.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(KeyPrefix.Container.class)
public @interface KeyPrefix {

    @NonNull String component();

    @NonNull String value();

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface Container {
        KeyPrefix[] value();
    }
}
