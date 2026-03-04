package com.g4stly.templateApp.dto.admin;

import com.g4stly.templateApp.models.enums.UserType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a user via the admin user-management API.
 * All fields are optional; only non-null values are applied (partial update).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserRequest {

    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    @Pattern(regexp = "^[+\\d\\s\\-()]*$", message = "Phone number contains invalid characters")
    private String phone;

    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    private String bio;

    /** Change the user's application-level type (e.g. WAITER → MANAGER). */
    private UserType userType;

    /** Manually flip the emailVerified flag (e.g. trusted import). */
    private Boolean emailVerified;
}
