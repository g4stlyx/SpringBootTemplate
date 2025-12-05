package  com.g4stly.templateApp.dto.auth;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    // Common fields for all user types
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$", 
             message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character (@$!%*?&)")
    private String password;
    
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;
    
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;
    
    // User type: "client" or "coach" (admins cannot register via API)
    @Pattern(regexp = "^(client|coach)$", message = "User type must be either 'client' or 'coach'")
    private String userType;
    
    // Optional fields for all users
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phone;
    
    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    private String bio;
    
    // Client-specific fields
    @Size(max = 200, message = "Occupation must not exceed 200 characters")
    private String occupation;
    
    // Coach-specific fields
    private List<String> specializations; // Array of specializations
    private List<String> certifications;  // Array of certifications
    
    @Min(value = 0, message = "Years of experience cannot be negative")
    @Max(value = 70, message = "Years of experience must be realistic")
    private Integer yearsOfExperience;
    
    @DecimalMin(value = "0.0", message = "Hourly rate cannot be negative")
    @DecimalMax(value = "100000.0", message = "Hourly rate must be realistic")
    private Double hourlyRate;
}