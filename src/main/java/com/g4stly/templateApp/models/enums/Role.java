package com.g4stly.templateApp.models.enums;

/**
 * Enum representing the high-level security role of an account in the system.
 * This separates regular users from administrators at the authentication boundary.
 *
 * - USER  → Regular application user (maps to ROLE_USER in Spring Security)
 * - ADMIN → System administrator with elevated access (maps to ROLE_ADMIN in Spring Security)
 *
 * For finer-grained user categorisation within the USER role, see {@link UserType}.
 */
public enum Role {
    USER,
    ADMIN
}
