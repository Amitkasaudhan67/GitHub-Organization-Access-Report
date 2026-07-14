package com.github.accessreport.model;

/** Normalized repository access levels exposed by the report API. */
public enum AccessPermission {
    READ(1),
    WRITE(2),
    ADMIN(3);

    private final int priority;

    AccessPermission(int priority) {
        this.priority = priority;
    }

    public AccessPermission strongest(AccessPermission other) {
        return priority >= other.priority ? this : other;
    }
}
