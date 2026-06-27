package com.hogwai.dynamodb.simplified;

/**
 * Interface for entities that support optimistic locking via version field.
 * <p>
 * When an entity implements this interface, the library automatically
 * increments the version on every write and adds condition checks
 * to prevent concurrent modification.
 */
public interface Versioned {
    /**
     * Returns the current version.
     */
    Integer getVersion();

    /**
     * Sets the version (called by the library after successful write).
     */
    void setVersion(Integer version);
}
