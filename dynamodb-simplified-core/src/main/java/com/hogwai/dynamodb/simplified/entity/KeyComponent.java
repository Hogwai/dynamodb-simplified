package com.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KeyComponent {

    @NonNull String component();

    int position() default 0;
}
