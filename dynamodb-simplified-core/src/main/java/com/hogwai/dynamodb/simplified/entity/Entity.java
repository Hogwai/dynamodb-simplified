package com.hogwai.dynamodb.simplified.entity;

import org.jspecify.annotations.NonNull;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Entity {

    @NonNull String discriminator();

    @NonNull String discriminatorAttribute() default "_type";

    @NonNull String table();
}
