package com.g4stly.templateApp.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for the "change email" flow.
 *
 * The user must confirm their current password to authorise the change.
 * A verification email will be sent to {@code newEmail}; the change is only
 * applied once that link is clicked.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeEmailRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New email is required")
    @Email(message = "New email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String newEmail;
}
