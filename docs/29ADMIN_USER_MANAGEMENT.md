# Admin User Management

## Overview

Admin-side endpoints for managing user accounts. Restricted to **admin level 0 (Super Admin)** and **level 1 (Admin)** only. Level 2 moderators have no access.

All endpoints are prefixed `/api/v1/admin/users`.

---

## Authorization

- Role: `ROLE_ADMIN`
- Level guard: `@adminLevelAuthorizationService.isLevel0Or1()` — levels 0 and 1 only
- Requesting admin's ID is always extracted from `Authentication.getDetails()` (JWT-sourced, not a path/body parameter)

---

## Endpoints

### GET `/api/v1/admin/users`

Returns a paginated, searchable, filterable list of all user accounts.

**Query Parameters:**

| Param           | Default     | Description                                       |
|-----------------|-------------|---------------------------------------------------|
| `page`          | `0`         | Zero-based page number                            |
| `size`          | `20`        | Items per page                                    |
| `sortBy`        | `createdAt` | Field to sort by (see allowed fields below)       |
| `sortDirection` | `desc`      | `asc` or `desc`                                   |
| `search`        | —           | Free-text match on `username` and `email`         |
| `isActive`      | —           | `true` / `false`                                  |
| `emailVerified` | —           | `true` / `false`                                  |
| `userType`      | —           | Enum value (e.g. `APP_USER`) — case-insensitive     |

**Allowed `sortBy` values:** `id`, `username`, `email`, `firstName`, `lastName`, `isActive`, `emailVerified`, `userType`, `createdAt`, `updatedAt`, `lastLoginAt`

**Success Response `200 OK`:**

```json
{
  "users": [ { ...AdminUserDTO } ],
  "currentPage": 0,
  "totalPages": 5,
  "totalItems": 98,
  "pageSize": 20
}
```

---

### GET `/api/v1/admin/users/{userId}`

Fetch a single user by ID.

**Success Response `200 OK`:** `AdminUserDTO`

**Errors:** `404 Not Found` if user does not exist.

---

### POST `/api/v1/admin/users`

Create a new user account.

**Request Body:**

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "Password1!",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+905001234567",
  "bio": "Staff member",
  "userType": "APP_USER",
  "isActive": true,
  "skipEmailVerification": false
}
```

| Field                   | Required | Notes                                                         |
|-------------------------|----------|---------------------------------------------------------------|
| `username`              | Yes      | 3–50 chars, alphanumeric + underscore                         |
| `email`                 | Yes      | Valid email, unique across users and admins                   |
| `password`              | Yes      | Min 8 chars                                                   |
| `userType`              | Yes      | Enum value (e.g. `APP_USER`)                                    |
| `skipEmailVerification` | No       | `false` by default — set `true` to pre-verify the account     |

**Success Response `201 Created`:** `AdminUserDTO`

When `skipEmailVerification=false`, a standard verification email is sent to the new address.

---

### PUT `/api/v1/admin/users/{userId}`

Partial update for a user account. All fields are optional — only provided (non-null) fields are applied.

**Request Body:**

```json
{
  "email": "newemail@example.com",
  "firstName": "Jane",
  "lastName": "Smith",
  "phone": "+905009876543",
  "bio": "Updated bio",
  "userType": "APP_USER",
  "emailVerified": true
}
```

> **Note:** If `email` is changed by an admin, `emailVerified` is automatically set to `false` regardless of the `emailVerified` field in the request. The user must re-verify their new address.

**Success Response `200 OK`:** `AdminUserDTO`

---

### POST `/api/v1/admin/users/{userId}/deactivate`

Soft-deactivates a user account.

- Sets `isActive = false`, `adminDeactivated = true`, `deactivatedAt = now()`
- Revokes all active refresh tokens for the user
- The user **cannot** reactivate their own account — only an admin can (see `/reactivate`)
- The account cleanup job does **not** process admin-deactivated accounts

**Success Response `200 OK`:** `AdminUserDTO` (with `isActive=false`, `adminDeactivated=true`)

**Errors:** `400 Bad Request` if the user is already inactive.

---

### POST `/api/v1/admin/users/{userId}/reactivate`

Reactivates a previously deactivated user account.

- Sets `isActive = true`, `adminDeactivated = false`, clears `deactivatedAt`
- Resets `loginAttempts = 0` and clears `lockedUntil`

**Success Response `200 OK`:** `AdminUserDTO`

**Errors:** `400 Bad Request` if the user is already active.

---

### DELETE `/api/v1/admin/users/{userId}`

**Hard delete — irreversible.** Permanently removes the user record.

Before deletion:
1. Revokes all refresh tokens (`role="user"`)
2. Deletes all `VerificationToken` records for `role="user"` and `role="email_change"`
3. Deletes the `User` row

**Success Response `200 OK`:**

```json
{
  "success": true,
  "message": "User permanently deleted"
}
```

---

### POST `/api/v1/admin/users/{userId}/reset-password`

Resets the user's password. Generates a new cryptographic salt and re-hashes using Argon2id.
Also clears `loginAttempts` and `lockedUntil`.

**Request Body:**

```json
{
  "newPassword": "FreshPass1!"
}
```

**Success Response `200 OK`:**

```json
{
  "success": true,
  "message": "Password reset successfully"
}
```

---

### POST `/api/v1/admin/users/{userId}/unlock`

Unlocks a user account that has been locked due to repeated failed login attempts.

- Resets `loginAttempts = 0`
- Clears `lockedUntil`

**Success Response `200 OK`:** `AdminUserDTO`

---

## AdminUserDTO

```json
{
  "id": 42,
  "username": "johndoe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "profilePicture": "https://cdn.example.com/photo.jpg",
  "phone": "+905001234567",
  "bio": "Staff member",
  "userType": "app_user",
  "isActive": true,
  "emailVerified": true,
  "adminDeactivated": false,
  "loginAttempts": 0,
  "lockedUntil": null,
  "deactivatedAt": null,
  "createdAt": "2025-01-10T09:00:00",
  "updatedAt": "2025-03-01T14:30:00",
  "lastLoginAt": "2025-03-03T08:15:00"
}
```

> `userType` is always returned as **lowercase** string (e.g. `"app_user"` not `"APP_USER"`).

---

## User Lifecycle — Deactivation States

```
Active
  │  (user self-deactivates)         (admin deactivates)
  ├──────────────────────────>  isActive=false, adminDeactivated=false
  │                             └── eligible for cleanup job after grace period
  │
  └──────────────────────────>  isActive=false, adminDeactivated=true
                                └── NOT eligible for cleanup job
                                └── user cannot self-reactivate
                                └── only admin /reactivate endpoint works
```

The `adminDeactivated` flag is the single source of truth for this distinction.

---

## Data Model Changes

### `users` table

| Column             | Type       | Notes                                                     |
|--------------------|------------|-----------------------------------------------------------|
| `admin_deactivated`| TINYINT(1) | Default `0`. Set to `1` when an admin deactivates the user |

---

## Security Notes

- All endpoints require admin JWT with level 0 or 1.
- Admin ID is sourced from the JWT token (`authentication.getDetails()`), not from a request parameter — prevents IDOR.
- Hard delete is permanent and irreversible. Consider using deactivate unless the account must be expunged.
- Password reset uses Argon2id with a fresh random salt — old credentials are immediately invalidated.

---

## Testing

| Test class                                    | Tests |
|-----------------------------------------------|-------|
| `AdminUserManagementServiceTest.java`  | 19    |
| `AdminUserManagementControllerTest.java` | 9   |

Key service-layer test cases:

| Test                                          | Assertion                                              |
|-----------------------------------------------|--------------------------------------------------------|
| `getUsers_invalidUserType_throws`             | `BadRequestException` on unknown enum value            |
| `createUser_usernameTaken_throws`             | `BadRequestException`                                  |
| `createUser_emailTaken_throws`                | `BadRequestException`                                  |
| `createUser_success_savesAndSendsEmail`       | Entity saved, verification email dispatched            |
| `createUser_skipVerification_doesNotSendEmail`| `emailVerified=true`, no email sent                    |
| `updateUser_emailChange_resetsEmailVerified`  | `emailVerified` forced to `false`                      |
| `deactivateUser_alreadyInactive_throws`       | `BadRequestException`                                  |
| `deactivateUser_success_setsFlags`            | `adminDeactivated=true`, tokens revoked                |
| `reactivateUser_alreadyActive_throws`         | `BadRequestException`                                  |
| `reactivateUser_success_clearsFlags`          | `isActive=true`, lock cleared                         |
| `hardDeleteUser_success_deletesAll`           | Tokens revoked, verification tokens removed, row deleted|
