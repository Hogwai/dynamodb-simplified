package com.hogwai.app.exception;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Singleton
@Produces
public class GlobalExceptionHandler implements ExceptionHandler<RuntimeException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, RuntimeException exception) {
        if (exception instanceof PostNotFoundException) {
            return HttpResponse.notFound(exception.getMessage());
        }
        if (exception instanceof UnauthorizedException) {
            return HttpResponse.unauthorized().body(exception.getMessage());
        }
        return HttpResponse.serverError(exception.getMessage());
    }
}
