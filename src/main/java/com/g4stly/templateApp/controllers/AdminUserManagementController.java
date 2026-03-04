package com.g4stly.templateApp.controllers;

import com.g4stly.templateApp.dto.admin.*;
import com.g4stly.templateApp.services.AdminUserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin-side user management endpoints.
 * Restricted to admins of level 0 or 1 — level 2 (moderator) has read-only
 * access to their own profile but no user-management authority.
 *
 * All userId values come from the path; the requesting admin's ID is always
 * sourced from authentication.getDetails() to enforce ownership at the JWT level.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0Or1()")
public class AdminUserManagementController {

    private final AdminUserManagementService adminUserManagementService;

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/admin/users
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a paginated, searchable list of all users with optional filters.
     *
     * Query params:
     *  - page, size, sortBy, sortDirection (pagination / sorting)
     *  - search       — free-text match on username and email
     *  - isActive     — true / false
     *  - emailVerified — true / false
     *  - userType     — e.g. WAITER
     */
    @GetMapping
    public ResponseEntity<AdminUserListResponse> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean emailVerified,
            @RequestParam(required = false) String userType,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} listing users — page={}, size={}, search={}", adminId, page, size, search);

        AdminUserListResponse response = adminUserManagementService.getUsers(
                adminId, page, size, sortBy, sortDirection,
                search, isActive, emailVerified, userType, httpRequest);

        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/admin/users/{userId}
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserDTO> getUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} fetching user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.getUser(adminId, userId, httpRequest));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/admin/users
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<AdminUserDTO> createUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} creating user with username={}", adminId, request.getUsername());
        AdminUserDTO dto = adminUserManagementService.createUser(adminId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/admin/users/{userId}
    // ──────────────────────────────────────────────────────────────────────────

    @PutMapping("/{userId}")
    public ResponseEntity<AdminUserDTO> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} updating user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.updateUser(adminId, userId, request, httpRequest));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/admin/users/{userId}/deactivate
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Admin soft-deactivates a user (sets isActive=false, adminDeactivated=true).
     * The user cannot reactivate themselves; only an admin can reactivate.
     */
    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<AdminUserDTO> deactivateUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} deactivating user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.deactivateUser(adminId, userId, httpRequest));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/admin/users/{userId}/reactivate
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<AdminUserDTO> reactivateUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} reactivating user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.reactivateUser(adminId, userId, httpRequest));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/admin/users/{userId}   — HARD DELETE (irreversible)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Permanently deletes the user record and all associated tokens.
     * This operation is irreversible. Use with care.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> hardDeleteUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.warn("Admin {} performing hard-delete on user {}", adminId, userId);
        adminUserManagementService.hardDeleteUser(adminId, userId, httpRequest);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User permanently deleted"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/admin/users/{userId}/reset-password
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetUserPasswordRequest request,
            Authentication authentication) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} resetting password for user {}", adminId, userId);
        adminUserManagementService.resetUserPassword(adminId, userId, request);
        return ResponseEntity.ok(Map.of("success", true, "message", "Password reset successfully"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/admin/users/{userId}/unlock
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/{userId}/unlock")
    public ResponseEntity<AdminUserDTO> unlockUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} unlocking user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.unlockUser(adminId, userId, httpRequest));
    }
}
