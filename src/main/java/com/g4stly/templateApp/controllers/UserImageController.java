package com.g4stly.templateApp.controllers;

import com.g4stly.templateApp.services.ImageUploadService;
import com.g4stly.templateApp.services.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Handles profile image upload/update/delete for regular users.
 *
 * Security:
 * - userId is always taken from the authenticated JWT details (never from the request).
 * - The image is validated via magic-byte detection inside {@link ImageUploadService}
 *   to prevent Content-Type spoofing (e.g. a PHP script uploaded as image/jpeg).
 */
@RestController
@RequestMapping("/api/v1/profile/image")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('USER')")
public class UserImageController {

    private final ImageUploadService imageUploadService;
    private final UserProfileService userProfileService;

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/profile/image  — upload (first time)
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            Long userId = (Long) authentication.getDetails();
            String imageUrl = imageUploadService.uploadProfileImage(file, "USER", userId);
            userProfileService.updateProfilePicture(userId, imageUrl);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile image uploaded successfully",
                    "imageUrl", imageUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error uploading user profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to upload profile image"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/profile/image  — replace existing image
    // ──────────────────────────────────────────────────────────────────────────

    @PutMapping
    public ResponseEntity<?> updateProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "oldImageUrl", required = false) String oldImageUrl,
            Authentication authentication) {
        try {
            Long userId = (Long) authentication.getDetails();
            String imageUrl = imageUploadService.updateProfileImage(file, "USER", userId, oldImageUrl);
            userProfileService.updateProfilePicture(userId, imageUrl);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile image updated successfully",
                    "imageUrl", imageUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating user profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to update profile image"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/profile/image  — remove image
    // ──────────────────────────────────────────────────────────────────────────

    @DeleteMapping
    public ResponseEntity<?> deleteProfileImage(
            @RequestParam(value = "imageUrl") String imageUrl,
            Authentication authentication) {
        try {
            Long userId = (Long) authentication.getDetails();
            imageUploadService.deleteImage(imageUrl);
            userProfileService.updateProfilePicture(userId, null);
            return ResponseEntity.ok(Map.of("success", true, "message", "Profile image removed successfully"));
        } catch (Exception e) {
            log.error("Error deleting user profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to delete profile image"));
        }
    }
}
