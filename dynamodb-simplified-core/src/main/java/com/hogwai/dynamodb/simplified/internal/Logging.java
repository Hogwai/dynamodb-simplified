package com.hogwai.dynamodb.simplified.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized SLF4J logger factory for the DynamoDB Simplified library.
 * <p>
 * All library classes should obtain their logger via this class
 * rather than using SLF4J's {@code LoggerFactory} directly.
 */
public final class Logging {
    private Logging() {
    }

    /**
     * Obtains an SLF4J {@link Logger} for the given class.
     *
     * @param clazz the class to name the logger after
     * @return a logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

}
