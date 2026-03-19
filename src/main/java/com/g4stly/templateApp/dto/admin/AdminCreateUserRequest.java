package com.g4stly.templateApp.dto.admin;

import com.g4stly.templateApp.models.enums.UserType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for admin-created user accounts.
 * Password will be set by the admin; the new user should be encouraged to
 * change it on first login.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    @Pattern(regexp = "^[+\\d\\s\\-()]*$", message = "Phone number contains invalid characters")
    private String phone;

    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    private String bio;

    /** The application-level role; defaults to APP_USER if omitted. */
    private UserType userType;

    /** Whether the account should start as active; defaults to true. */
    private Boolean isActive;

    /**
     * Whether to skip email verification and immediately mark the account as
     * verified.
     * Defaults to false (sends a verification email).
     */
    private Boolean skipEmailVerification;
}
