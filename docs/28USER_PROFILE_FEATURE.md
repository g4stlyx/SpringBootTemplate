# User Profile Feature

## Overview

Self-service profile management for regular users (`ROLE_USER`). Users can view and update their profile, change their password, deactivate their account (with a 30-day reactivation window), and manage their profile picture.

All endpoints enforce **ownership at the JWT level** — the `userId` is always extracted from `authentication.getDetails()` (set by `JwtAuthFilter` during token validation). No `userId` is accepted via path or query parameters, eliminating IDOR attack vectors.

---

## Architecture

```
UserProfileController   →   UserProfileService    →   UserRepository
UserImageController     →   UserProfileService    →   AdminRepository (email uniqueness)
                        →   ImageUploadService    →   Cloudflare R2
                        →   RefreshTokenService   →   RefreshTokenRepository
                        →   PasswordService       →   Argon2id
```

Scheduled cleanup:

```
AccountCleanupScheduledService  →  UserRepository         (anonymise PII)
                                →  RefreshTokenRepository (revoke sessions)
                                →  ImageUploadService     (delete R2 object)
```

---

## Endpoints

### Authentication

All endpoints require `Authorization: Bearer <accessToken>` and `ROLE_USER`.  
Admins (`ROLE_ADMIN`) do **not** have access to these endpoints.

---

### GET `/api/v1/profile`

Returns the authenticated user's profile.

**Response 200**
```json
{
  "id": 1,
  "username": "johndoe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "profilePicture": "https://cdn.example.com/users/1/profile.jpg",
  "phone": "+905551234567",
  "bio": "Short bio",
  "userType": "waiter",
  "isActive": true,
  "emailVerified": true,
  "createdAt": "2025-01-01T10:00:00",
  "updatedAt": "2025-06-01T12:00:00",
  "lastLoginAt": "2025-06-15T09:30:00"
}
```

---

### PUT `/api/v1/profile`

Partial profile update. Only fields provided (non-null) in the request body are applied.

**Request Body**
```json
{
  "email": "newemail@example.com",
  "firstName": "Jane",
  "lastName": "Smith",
  "phone": "+905559876543",
  "bio": "Updated bio"
}
```

All fields are optional. Email must be unique across both users and admins tables.

**Response 200** — Updated `UserProfileDTO` (same shape as GET).

**Response 400** — `{ "message": "Email is already in use" }` if the email is taken.

---

### POST `/api/v1/profile/change-password`

Changes the user's password. Requires both current and new password confirmation.

**Request Body**
```json
{
  "currentPassword": "OldPassword1!",
  "newPassword": "NewPassword1!",
  "confirmPassword": "NewPassword1!"
}
```

**Validation Rules**
- `newPassword` must differ from `currentPassword`
- `newPassword` must equal `confirmPassword`
- `currentPassword` must be verified via Argon2id

**Response 200**
```json
{
  "success": true,
  "message": "Password changed successfully"
}
```

**Response 400** — validation failures (mismatch, same as old, wrong current).

---

### DELETE `/api/v1/profile`

Soft-deactivates the authenticated user's own account. Password confirmation is required to prevent accidental or CSRF-driven closure.

**Request Body**
```json
{
  "password": "MyCurrentPassword1!"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Account deactivated. You may reactivate it by logging in within 30 days."
}
```

**Side Effects**
1. `isActive = false`, `deactivatedAt = now()`
2. All active refresh tokens revoked immediately (no continued session access)
3. The nightly `AccountCleanupScheduledService` will permanently anonymise PII after 30 days

**Response 400** — `{ "message": "Password is incorrect" }` if password is wrong.

---

### POST `/api/v1/profile/image`

Uploads a profile picture for the first time.

**Request** — `multipart/form-data` with field `file`

**Validation** — Magic-byte detection (JPEG, PNG, GIF, BMP, WebP), max 5 MB, performed by `ImageUploadService` before any upload to R2.

**Response 200**
```json
{
  "success": true,
  "message": "Profile image uploaded successfully",
  "imageUrl": "https://cdn.example.com/USER/1/profile_1718000000.jpg"
}
```

**Response 400** — `{ "success": false, "message": "Invalid file type" }` for unsupported/spoofed files.

---

### PUT `/api/v1/profile/image`

Replaces an existing profile picture.

**Request** — `multipart/form-data` with fields:
- `file` (required) — the new image
- `oldImageUrl` (optional) — the current R2 URL to delete after successful upload

**Response 200** — same shape as POST.

---

### DELETE `/api/v1/profile/image`

Removes the profile picture.

**Request** — Query parameter `imageUrl` (R2 URL to delete).

**Response 200**
```json
{
  "success": true,
  "message": "Profile image removed successfully"
}
```

---

## Account Deactivation Lifecycle

```
User calls DELETE /api/v1/profile
         │
         ▼
  isActive=false, deactivatedAt=now()
  All refresh tokens revoked
         │
         ▼
  Grace period: 30 days
  (configurable via ACCOUNT_DEACTIVATION_GRACE_PERIOD_DAYS env var)
         │
  Login during grace period?
  ├── Yes → account reactivated automatically, login succeeds
  │         response message: "Account reactivated. Login successful."
  └── No  → grace period expires
             │
             ▼
       AccountCleanupScheduledService (02:30 daily)
       ├── Deletes R2 profile picture
       ├── Revokes any remaining refresh tokens
       └── Anonymises PII:
           username  → "deleted_{id}"
           email     → "deleted_{id}@deleted.invalid"
           firstName, lastName, phone, bio, profilePicture → null
           passwordHash, salt → "ANONYMISED"
```

---

## Configuration

| Property | Env Var | Default | Description |
|---|---|---|---|
| `app.account.deactivation.grace-period-days` | `ACCOUNT_DEACTIVATION_GRACE_PERIOD_DAYS` | `30` | Days before PII is permanently anonymised |

---

## Security Notes

- **Ownership**: `userId` is always from `authentication.getDetails()`. No path/query parameter accepted. IDOR is structurally impossible.
- **Password confirmation**: Both change-password and account deactivation require current password verification via Argon2id to prevent CSRF-based account closure.
- **Magic-byte validation**: Image uploads validate actual file content, not the `Content-Type` header, to prevent script injection via spoofed MIME types.
- **Token revocation**: Deactivation immediately revokes all refresh tokens so existing sessions terminate without waiting for JWT expiry.
- **Role isolation**: All endpoints are `hasRole('USER')` only. Admins manage their own profiles via `/api/v1/admin/profile/**`.

---

## Files Created / Modified

| File | Action |
|---|---|
| `models/User.java` | Added `deactivatedAt` field |
| `repos/UserRepository.java` | Added `findByIsActiveFalseAndDeactivatedAtBefore()` |
| `dto/user/UserProfileDTO.java` | New — response DTO |
| `dto/user/UpdateUserProfileRequest.java` | New — partial update request DTO |
| `dto/user/DeactivateAccountRequest.java` | New — password confirmation DTO |
| `services/UserProfileService.java` | New — business logic |
| `controllers/UserProfileController.java` | New — GET/PUT/POST/DELETE endpoints |
| `controllers/UserImageController.java` | New — image upload/update/delete endpoints |
| `services/scheduled/AccountCleanupScheduledService.java` | New — nightly PII anonymisation job |
| `services/AuthService.java` | Modified — grace period reactivation on login |
| `config/SecurityConfig.java` | Modified — 7 new route rules for `/api/v1/profile/**` |
| `resources/application.properties` | Added grace period property |
