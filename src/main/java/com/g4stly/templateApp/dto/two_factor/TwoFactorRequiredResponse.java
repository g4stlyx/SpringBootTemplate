package  com.g4stly.templateApp.dto.two_factor;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TwoFactorRequiredResponse {
    private String message;
    private String username;
    private boolean requiresTwoFactor;
    private String twoFactorChallengeToken; // Challenge token to validate 2FA submission
}
