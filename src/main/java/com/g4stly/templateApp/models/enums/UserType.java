package com.g4stly.templateApp.models.enums;

/**
 * Enum representing the application-level type of a regular user.
 * All values in this enum map to ROLE_USER at the Spring Security level.
 * Use {@link Role} to distinguish between regular users and administrators.
 *
 * Add new types here as the application evolves.
 */
public enum UserType {
    WAITER,  // Waiter staff
    CHEF     // Kitchen staff — add more types here as the application evolves.
}
