package com.g4stly.templateApp.dto.refresh_token;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    // Used by mobile clients that send refresh token in the request body
    private String refreshToken;
}
