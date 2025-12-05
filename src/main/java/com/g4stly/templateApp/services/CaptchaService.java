package com.g4stly.templateApp.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Service for verifying Google reCAPTCHA v2 and v3 tokens
 */
@Service
public class CaptchaService {

    private static final Logger logger = LoggerFactory.getLogger(CaptchaService.class);
    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    @Value("${recaptcha.secret-key:}")
    private String secretKey;

    @Value("${recaptcha.enabled:false}")
    private boolean enabled;

    @Value("${recaptcha.score-threshold:0.5}")
    private double scoreThreshold;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Verify reCAPTCHA token
     * @param token The reCAPTCHA token from the frontend
     * @param remoteIp The user's IP address (optional but recommended)
     * @return true if verification passes, false otherwise
     */
    public boolean verifyCaptcha(String token, String remoteIp) {
        // Skip verification if reCAPTCHA is disabled
        if (!enabled) {
            logger.warn("reCAPTCHA verification is disabled");
            return true;
        }

        if (token == null || token.isEmpty()) {
            logger.warn("No reCAPTCHA token provided");
            return false;
        }

        if (secretKey == null || secretKey.isEmpty()) {
            logger.error("reCAPTCHA secret key not configured");
            return false;
        }

        try {
            // Prepare request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("secret", secretKey);
            params.add("response", token);
            if (remoteIp != null && !remoteIp.isEmpty()) {
                params.add("remoteip", remoteIp);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // Send request to Google
            ResponseEntity<RecaptchaResponse> response = restTemplate.postForEntity(
                    RECAPTCHA_VERIFY_URL,
                    request,
                    RecaptchaResponse.class
            );

            RecaptchaResponse recaptchaResponse = response.getBody();
            
            if (recaptchaResponse == null) {
                logger.error("Empty response from reCAPTCHA verification");
                return false;
            }

            // Log the result
            logger.info("reCAPTCHA verification result: success={}, score={}, action={}, errorCodes={}",
                    recaptchaResponse.isSuccess(),
                    recaptchaResponse.getScore(),
                    recaptchaResponse.getAction(),
                    recaptchaResponse.getErrorCodes());

            // For reCAPTCHA v3, also check the score
            if (recaptchaResponse.getScore() != null) {
                boolean scorePass = recaptchaResponse.getScore() >= scoreThreshold;
                if (!scorePass) {
                    logger.warn("reCAPTCHA score {} is below threshold {}", 
                            recaptchaResponse.getScore(), scoreThreshold);
                }
                return recaptchaResponse.isSuccess() && scorePass;
            }

            // For reCAPTCHA v2, just check success
            return recaptchaResponse.isSuccess();

        } catch (Exception e) {
            logger.error("Error verifying reCAPTCHA", e);
            return false;
        }
    }

    /**
     * DTO for reCAPTCHA response
     */
    @Data
    private static class RecaptchaResponse {
        private boolean success;
        
        @JsonProperty("challenge_ts")
        private String challengeTs;
        
        private String hostname;
        
        private Double score; // For reCAPTCHA v3
        
        private String action; // For reCAPTCHA v3
        
        @JsonProperty("error-codes")
        private List<String> errorCodes;
    }
}
