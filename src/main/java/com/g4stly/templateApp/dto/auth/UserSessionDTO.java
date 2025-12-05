package  com.g4stly.templateApp.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal user session DTO for /auth/me endpoint
 * Returns only essential data needed for UI header and session
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSessionDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String profilePicture;
    private String role; // "CLIENT", "COACH", "ADMIN"
    private Integer adminLevel; // Only for admins
}
