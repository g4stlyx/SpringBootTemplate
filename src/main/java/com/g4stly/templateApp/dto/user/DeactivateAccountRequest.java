package com.g4stly.templateApp.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Requires the user to confirm their current password before self-deactivating.
 * This prevents accidental or CSRF-driven account closure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeactivateAccountRequest {

    @NotBlank(message = "Password confirmation is required to deactivate your account")
    private String password;
}
