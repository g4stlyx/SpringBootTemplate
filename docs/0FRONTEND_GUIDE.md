# FRONTEND DEVELOPER GUIDE

**Template Java Spring Boot REST API - Complete Frontend Integration Guide**

---

## TABLE OF CONTENTS

1. [Overview](#overview)
2. [Authentication System](#authentication-system)
3. [Security & Authorization](#security--authorization)
4. [API Base Configuration](#api-base-configuration)
5. [Authentication Endpoints](#authentication-endpoints)
6. [Two-Factor Authentication (2FA)](#two-factor-authentication-2fa)
7. [Refresh Token System](#refresh-token-system)
8. [Admin Profile Management](#admin-profile-management)
9. [Admin Management](#admin-management)
10. [Image Upload System](#image-upload-system)
11. [Admin Logging Endpoints](#admin-logging-endpoints)
12. [Database Backup](#database-backup)
13. [Error Handling](#error-handling)
14. [Rate Limiting](#rate-limiting)
15. [Request/Response Patterns](#requestresponse-patterns)
16. [Best Practices](#best-practices)

---

## OVERVIEW

This API is a production-ready Spring Boot REST API with comprehensive authentication, authorization, and admin management features. It supports multiple user types (admin, client, coach) with hierarchical permission levels.

### Key Features
- JWT-based authentication with access & refresh tokens
- Two-Factor Authentication (2FA) for admins
- Google reCAPTCHA for admin logins
- Argon2id password hashing with pepper & salt
- Role-based access control (RBAC) with admin levels (0, 1, 2)
- Comprehensive logging (admin activity, auth errors, sensitive access, user activity)
- Image upload with Cloudflare R2 integration
- Email notifications (verification, password reset)
- Token rotation for enhanced security

### Base URL
```
http://localhost:8080/api/v1
```

### Content Type
All endpoints accept and return JSON unless specified otherwise (e.g., multipart/form-data for file uploads).

```
Content-Type: application/json
```

---

## AUTHENTICATION SYSTEM

### Authentication Flow

#### Standard Login Flow
1. User submits credentials to `/auth/login`
2. Server validates credentials
3. Server returns:
   - **Access Token** (JWT, 15-minute expiration)
   - **Refresh Token** (30-day expiration)
   - User information

#### Admin Login with 2FA Flow
1. Admin submits credentials to `/auth/login` with CAPTCHA token
2. If 2FA is enabled, server returns **202 Accepted** with:
   ```json
   {
     "message": "Two-factor authentication required",
     "username": "admin_username",
     "requiresTwoFactor": true
   }
   ```
3. Frontend prompts for 6-digit 2FA code
4. Submit to `/admin/2fa/verify-login`
5. Server returns full auth response with tokens

### Token Storage

#### Web Applications
- **Refresh Token**: Stored in HTTP-only secure cookie (automatically managed)
- **Access Token**: Store in memory or secure session storage (never in localStorage)

#### Mobile Applications
- **Both tokens**: Store in secure storage (Keychain/Keystore)
- Include `refreshToken` in request body for `/auth/refresh`

### Token Refresh Flow
1. Access token expires (15 minutes)
2. Frontend automatically calls `/auth/refresh`
3. Send refresh token (cookie for web, body for mobile)
4. Receive new access token + new refresh token (token rotation)
5. Update stored tokens

---

## SECURITY & AUTHORIZATION

### Authorization Header
Include JWT access token in all authenticated requests:

```
Authorization: Bearer <access_token>
```

### User Types
- `admin` - Administrative users with levels 0, 1, or 2
- `client` - Standard user type
- `coach` - Professional user type

### Admin Permission Levels

| Level | Name | Permissions |
|-------|------|-------------|
| 0 | Super Admin | Full access to all endpoints including logs, token management, database backup |
| 1 | Admin | Admin management, profile access, image uploads |
| 2 | Basic Admin | Profile management, image uploads |

### Authorization Rules

#### Level 0 Only (Super Admin)
- Admin Activity Logs
- Auth Error Logs
- Sensitive Access Logs
- User Activity Logs
- Token Management (verification & password reset tokens)
- Refresh Token Management
- Database Backup

#### Level 0 or 1
- Admin CRUD operations
- Admin activation/deactivation
- Password resets for other admins

#### Level 0, 1, or 2
- Own profile management
- Profile image uploads
- 2FA setup/disable
- Change own password

### CAPTCHA Requirement
- **Admin logins** require Google reCAPTCHA when enabled
- Include `captchaToken` in login request for admin users
- Regular users (client/coach) do not require CAPTCHA

---

## API BASE CONFIGURATION

### Environment Variables
Your frontend should configure these based on environment:

```javascript
// Example configuration
const API_CONFIG = {
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1',
  accessTokenExpiration: 900000, // 15 minutes in ms
  refreshTokenExpiration: 2592000000, // 30 days in ms
  captchaSiteKey: process.env.REACT_APP_CAPTCHA_SITE_KEY,
  useCookiesForRefreshToken: true // false for mobile apps
};
```

### Axios/Fetch Interceptor Setup

#### Request Interceptor
```javascript
// Add access token to all requests
axios.interceptors.request.use(
  config => {
    const token = getAccessToken(); // from memory/session
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => Promise.reject(error)
);
```

#### Response Interceptor (Token Refresh)
```javascript
axios.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config;
    
    // If 401 and not already retried
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        // Refresh token
        const response = await axios.post('/auth/refresh', {
          // Include refreshToken for mobile apps
          // For web, it's sent via cookie automatically
        });
        
        const { accessToken, refreshToken } = response.data;
        
        // Update stored tokens
        setAccessToken(accessToken);
        if (refreshToken) setRefreshToken(refreshToken); // mobile only
        
        // Retry original request
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return axios(originalRequest);
      } catch (refreshError) {
        // Refresh failed - logout user
        logout();
        return Promise.reject(refreshError);
      }
    }
    
    return Promise.reject(error);
  }
);
```

---

## AUTHENTICATION ENDPOINTS

### 1. Register User

**Endpoint:** `POST /auth/register`

**Description:** Register a new user account (client or coach).

**Request Body:**
```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "userType": "client" // or "coach"
}
```

**Validation Rules:**
- Username: 3-50 characters, alphanumeric + underscore
- Email: Valid email format
- Password: Minimum 8 characters
- UserType: "client" or "coach" (admin accounts cannot be created via register)

**Success Response (200):**
```json
{
  "success": true,
  "message": "Registration successful. Please check your email to verify your account.",
  "accessToken": null,
  "refreshToken": null,
  "user": null
}
```

**Note:** User must verify email before logging in. Access token not provided until email verification.

**Error Response (400):**
```json
{
  "success": false,
  "message": "Username already exists"
}
```

---

### 2. Login

**Endpoint:** `POST /auth/login`

**Description:** Authenticate user and receive tokens.

**Request Body:**
```json
{
  "username": "johndoe",
  "password": "SecurePass123!",
  "userType": "client", // "client", "coach", or "admin"
  "captchaToken": "recaptcha_token_here" // Required only for admin logins
}
```

**Success Response - Standard Login (200):**
```json
{
  "success": true,
  "message": "Login successful",
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "expiresIn": 900000,
  "requiresTwoFactor": false,
  "user": {
    "id": 1,
    "username": "johndoe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "profilePicture": "https://cdn.example.com/profile.jpg",
    "isActive": true,
    "emailVerified": true,
    "userType": "client",
    "level": null,
    "lastLoginAt": "2026-02-12T10:30:00"
  }
}
```

**Success Response - 2FA Required (202):**
```json
{
  "message": "Two-factor authentication required. Please enter your verification code.",
  "username": "admin_user",
  "requiresTwoFactor": true,
  "tempToken": null
}
```

**Frontend Action for 202:**
1. Store username temporarily
2. Redirect to 2FA verification page
3. Prompt for 6-digit code
4. Submit to `/admin/2fa/verify-login`

**Error Response - Email Not Verified (401):**
```json
{
  "success": false,
  "message": "Please verify your email before logging in"
}
```

**Error Response - Invalid Credentials (401):**
```json
{
  "success": false,
  "message": "Invalid username or password"
}
```

**Error Response - Admin Without CAPTCHA (400):**
```json
{
  "error": "CAPTCHA is required for admin login",
  "captchaRequired": true
}
```

---

### 3. Verify Email

**Endpoint:** `GET /auth/verify-email?token={verification_token}`

**Description:** Verify user's email address using token from email.

**Query Parameters:**
- `token` (required): Verification token from email link

**Success Response (200):**
```json
{
  "success": true,
  "message": "Email verified successfully. You can now login to your account."
}
```

**Error Response (200):**
```json
{
  "success": false,
  "message": "Invalid or expired verification token."
}
```

**Frontend Implementation:**
1. Extract token from URL query parameter
2. Call endpoint with token
3. Display success message and redirect to login
4. Or display error and offer to resend verification

---

### 4. Resend Verification Email

**Endpoint:** `POST /auth/resend-verification`

**Description:** Resend verification email to user.

**Request Body:**
```json
{
  "email": "john@example.com"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "If the email exists and is not verified, a new verification link has been sent."
}
```

**Note:** Response is intentionally vague for security (doesn't reveal if email exists).

---

### 5. Forgot Password

**Endpoint:** `POST /auth/forgot-password`

**Description:** Initiate password reset process.

**Request Body:**
```json
{
  "email": "john@example.com",
  "userType": "client" // "client", "coach", or "admin"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "If the email exists in our system, a password reset link has been sent."
}
```

**Note:** Response is intentionally vague for security (doesn't reveal if email exists).

---

### 6. Reset Password

**Endpoint:** `POST /auth/reset-password`

**Description:** Reset password using token from email.

**Request Body:**
```json
{
  "token": "reset_token_from_email",
  "newPassword": "NewSecurePass123!"
}
```

**Validation:**
- Password: Minimum 8 characters

**Success Response (200):**
```json
{
  "success": true,
  "message": "Password has been reset successfully. You can now login with your new password."
}
```

**Error Response (200):**
```json
{
  "success": false,
  "message": "Invalid or expired reset token. Please request a new password reset."
}
```

---

### 7. Verify Password (for Password Change)

**Endpoint:** `POST /auth/verify-password`

**Description:** Verify current password before allowing password change.

**Request Body:**
```json
{
  "username": "johndoe",
  "password": "CurrentPassword123!",
  "userType": "client"
}
```

**Response (200):**
```json
{
  "valid": true,
  "message": "Password is valid"
}
```

**Use Case:** Called before showing change password form to ensure user knows current password.

---

### 8. Get Current User Session

**Endpoint:** `GET /auth/me`

**Authorization:** Bearer token required

**Description:** Get minimal current user information for UI (name, profile picture, role).

**Response (200):**
```json
{
  "id": 1,
  "username": "johndoe",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "profilePicture": "https://cdn.example.com/profile.jpg",
  "userType": "client",
  "level": null
}
```

**Use Case:** Called on app load to populate user info in header/sidebar. Lightweight alternative to fetching full profile.

---

### 9. Health Check

**Endpoint:** `GET /auth/health`

**Description:** Check if authentication service is running.

**Response (200):**
```json
{
  "status": "UP",
  "service": "Authentication Service",
  "timestamp": 1707738600000
}
```

---

## TWO-FACTOR AUTHENTICATION (2FA)

**Note:** 2FA is available only for admin users.

### 1. Setup 2FA

**Endpoint:** `POST /admin/2fa/setup`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Description:** Generate 2FA secret and QR code for admin.

**Response (200):**
```json
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeUrl": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
  "manualEntryKey": "JBSWY3DPEHPK3PXP",
  "issuer": "Template App",
  "accountName": "admin@example.com"
}
```

**Frontend Implementation:**
1. Display QR code image (`qrCodeUrl`)
2. Provide manual entry option (`manualEntryKey`)
3. User scans with authenticator app (Google Authenticator, Authy, etc.)
4. Prompt for 6-digit code to verify
5. Call `/admin/2fa/verify` to enable

---

### 2. Verify and Enable 2FA

**Endpoint:** `POST /admin/2fa/verify`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Description:** Verify 6-digit code and enable 2FA.

**Request Body:**
```json
{
  "code": "123456"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "2FA has been enabled successfully"
}
```

**Error Response (200):**
```json
{
  "success": false,
  "message": "Invalid verification code"
}
```

---

### 3. Verify 2FA During Login

**Endpoint:** `POST /admin/2fa/verify-login`

**Authorization:** None (public endpoint, part of login flow)

**Description:** Complete 2FA login by verifying code.

**Request Body:**
```json
{
  "username": "admin_user",
  "code": "123456"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Admin login successful",
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "expiresIn": 900000,
  "user": {
    "id": 1,
    "username": "admin_user",
    "email": "admin@example.com",
    "firstName": "Admin",
    "lastName": "User",
    "profilePicture": null,
    "isActive": true,
    "emailVerified": true,
    "userType": "admin",
    "level": 0,
    "lastLoginAt": "2026-02-12T10:30:00"
  }
}
```

**Error Response (401):**
```json
{
  "success": false,
  "message": "Invalid verification code"
}
```

---

### 4. Check 2FA Status

**Endpoint:** `GET /admin/2fa/status`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Description:** Check if 2FA is enabled for current admin.

**Response (200):**
```json
{
  "enabled": true
}
```

---

### 5. Disable 2FA

**Endpoint:** `POST /admin/2fa/disable`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Description:** Disable 2FA (requires verification code).

**Request Body:**
```json
{
  "code": "123456"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "2FA has been disabled successfully"
}
```

**Error Response (400):**
```json
{
  "success": false,
  "message": "Invalid verification code"
}
```

---

## REFRESH TOKEN SYSTEM

### 1. Refresh Access Token

**Endpoint:** `POST /auth/refresh`

**Description:** Get new access token using refresh token. Implements token rotation (old refresh token revoked, new one issued).

**Request Body (Mobile Apps):**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Request Body (Web Apps):**
```json
{}
```
*Web apps send refresh token via HTTP-only cookie automatically*

**Success Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 900000,
  "refreshToken": "660e8400-e29b-41d4-a716-446655440001" // Included for mobile only
}
```

**Error Response (401):**
```json
{
  "error": "Invalid or expired refresh token. Please login again."
}
```

**Token Rotation:**
- Old refresh token is revoked
- New refresh token is created
- Both access and refresh tokens are refreshed
- For web: New refresh token set in HTTP-only cookie

---

### 2. Logout (Current Device)

**Endpoint:** `POST /auth/logout`

**Description:** Logout from current device by revoking refresh token.

**Request Body (Mobile):**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Request Body (Web):**
```json
{}
```

**Response (200):**
```json
{
  "message": "Logged out successfully",
  "success": true
}
```

**Frontend Action:**
1. Call logout endpoint
2. Clear access token from memory
3. Clear refresh token from storage (mobile only, web cookie cleared by server)
4. Redirect to login

---

### 3. Logout from All Devices

**Endpoint:** `POST /auth/logout-all`

**Authorization:** Bearer token required

**Description:** Logout from all devices by revoking all refresh tokens for the user.

**Response (200):**
```json
{
  "message": "Logged out from all devices successfully",
  "revokedTokens": 3,
  "success": true
}
```

**Use Case:** User suspects account compromise or wants to logout from all active sessions.

---

## ADMIN PROFILE MANAGEMENT

**Authorization:** Level 0, 1, or 2 admins only

### 1. Get Own Profile

**Endpoint:** `GET /admin/profile`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Response (200):**
```json
{
  "id": 1,
  "username": "admin_user",
  "email": "admin@example.com",
  "firstName": "Admin",
  "lastName": "User",
  "profilePicture": "https://cdn.example.com/admin/profile/1.jpg",
  "isActive": true,
  "level": 0,
  "createdAt": "2026-01-15T08:00:00",
  "updatedAt": "2026-02-12T10:30:00",
  "lastLoginAt": "2026-02-12T10:30:00",
  "twoFactorEnabled": true
}
```

---

### 2. Get Admin Profile by ID

**Endpoint:** `GET /admin/profile/{adminId}`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Description:** Get any admin's profile (for higher level admins).

**Response (200):**
```json
{
  "id": 2,
  "username": "another_admin",
  "email": "admin2@example.com",
  "firstName": "Another",
  "lastName": "Admin",
  "profilePicture": null,
  "isActive": true,
  "level": 2,
  "createdAt": "2026-01-20T08:00:00",
  "updatedAt": "2026-02-10T14:30:00",
  "lastLoginAt": "2026-02-10T14:30:00",
  "twoFactorEnabled": false
}
```

---

### 3. Update Own Profile

**Endpoint:** `PUT /admin/profile`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Request Body:**
```json
{
  "firstName": "UpdatedFirst",
  "lastName": "UpdatedLast",
  "email": "newemail@example.com"
}
```

**Validation:**
- All fields optional
- Email must be valid format if provided
- Cannot update username or level

**Response (200):**
```json
{
  "id": 1,
  "username": "admin_user",
  "email": "newemail@example.com",
  "firstName": "UpdatedFirst",
  "lastName": "UpdatedLast",
  "profilePicture": "https://cdn.example.com/admin/profile/1.jpg",
  "isActive": true,
  "level": 0,
  "createdAt": "2026-01-15T08:00:00",
  "updatedAt": "2026-02-12T11:00:00",
  "lastLoginAt": "2026-02-12T10:30:00",
  "twoFactorEnabled": true
}
```

---

### 4. Change Password

**Endpoint:** `POST /admin/profile/change-password`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Request Body:**
```json
{
  "currentPassword": "OldPassword123!",
  "newPassword": "NewPassword123!"
}
```

**Validation:**
- currentPassword: Required, must be correct
- newPassword: Minimum 8 characters
- newPassword cannot be same as currentPassword

**Success Response (200):**
```json
{
  "success": true,
  "message": "Password changed successfully"
}
```

**Error Response (400):**
```json
{
  "success": false,
  "message": "Current password is incorrect"
}
```

---

### 5. Deactivate Admin Account

**Endpoint:** `POST /admin/profile/{adminId}/deactivate`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Description:** Deactivate another admin's account (requires higher level).

**Response (200):**
```json
{
  "success": true,
  "message": "Admin account deactivated successfully"
}
```

---

### 6. Reactivate Admin Account

**Endpoint:** `POST /admin/profile/{adminId}/reactivate`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Description:** Reactivate a deactivated admin account.

**Response (200):**
```json
{
  "success": true,
  "message": "Admin account reactivated successfully"
}
```

---

## ADMIN MANAGEMENT

**Authorization:** Level 0 or 1 admins only

### 1. Get All Admins (Paginated)

**Endpoint:** `GET /admin/admins`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Query Parameters:**
- `page` (default: 0) - Page number
- `size` (default: 10) - Items per page
- `sortBy` (default: createdAt) - Sort field
- `sortDirection` (default: desc) - Sort direction (asc/desc)

**Example:** `GET /admin/admins?page=0&size=20&sortBy=username&sortDirection=asc`

**Response (200):**
```json
{
  "admins": [
    {
      "id": 1,
      "username": "admin_user",
      "email": "admin@example.com",
      "firstName": "Admin",
      "lastName": "User",
      "profilePicture": null,
      "isActive": true,
      "level": 0,
      "createdAt": "2026-01-15T08:00:00",
      "updatedAt": "2026-02-12T10:30:00",
      "lastLoginAt": "2026-02-12T10:30:00"
    }
  ],
  "currentPage": 0,
  "totalPages": 1,
  "totalItems": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

**Permission Filtering:**
- Level 0: Can see all admins
- Level 1: Can see only Level 1 and 2 admins (cannot see Level 0)

---

### 2. Get Admin by ID

**Endpoint:** `GET /admin/admins/{adminId}`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Response (200):**
```json
{
  "id": 1,
  "username": "admin_user",
  "email": "admin@example.com",
  "firstName": "Admin",
  "lastName": "User",
  "profilePicture": null,
  "isActive": true,
  "level": 0,
  "createdAt": "2026-01-15T08:00:00",
  "updatedAt": "2026-02-12T10:30:00",
  "lastLoginAt": "2026-02-12T10:30:00"
}
```

---

### 3. Create Admin

**Endpoint:** `POST /admin/admins`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Request Body:**
```json
{
  "username": "new_admin",
  "email": "newadmin@example.com",
  "password": "SecurePass123!",
  "firstName": "New",
  "lastName": "Admin",
  "level": 2
}
```

**Validation Rules:**
- Username: 3-50 characters, unique
- Email: Valid format, unique
- Password: Minimum 8 characters
- Level: 0, 1, or 2
- Level 1 admins can only create Level 2 admins
- Level 0 admins can create any level

**Success Response (201):**
```json
{
  "id": 3,
  "username": "new_admin",
  "email": "newadmin@example.com",
  "firstName": "New",
  "lastName": "Admin",
  "profilePicture": null,
  "isActive": true,
  "level": 2,
  "createdAt": "2026-02-12T11:00:00",
  "updatedAt": "2026-02-12T11:00:00",
  "lastLoginAt": null
}
```

---

### 4. Update Admin

**Endpoint:** `PUT /admin/admins/{adminId}`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Request Body:**
```json
{
  "firstName": "Updated",
  "lastName": "Name",
  "email": "updated@example.com",
  "level": 2
}
```

**Validation:**
- Cannot update own level
- Level 1 cannot update Level 0 admins
- All fields optional

**Response (200):**
```json
{
  "id": 3,
  "username": "new_admin",
  "email": "updated@example.com",
  "firstName": "Updated",
  "lastName": "Name",
  "profilePicture": null,
  "isActive": true,
  "level": 2,
  "createdAt": "2026-02-12T11:00:00",
  "updatedAt": "2026-02-12T11:30:00",
  "lastLoginAt": null
}
```

---

### 5. Delete Admin (Soft Delete)

**Endpoint:** `DELETE /admin/admins/{adminId}`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Description:** Deactivate admin account (soft delete).

**Response (200):**
```json
{
  "success": true,
  "message": "Admin deactivated successfully"
}
```

**Restrictions:**
- Cannot delete yourself
- Level 1 cannot delete Level 0 admins

---

### 6. Activate Admin

**Endpoint:** `POST /admin/admins/{adminId}/activate`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Response (200):**
```json
{
  "id": 3,
  "username": "new_admin",
  "isActive": true,
  ...
}
```

---

### 7. Deactivate Admin

**Endpoint:** `POST /admin/admins/{adminId}/deactivate`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Response (200):**
```json
{
  "id": 3,
  "username": "new_admin",
  "isActive": false,
  ...
}
```

---

### 8. Reset Admin Password

**Endpoint:** `POST /admin/admins/{adminId}/reset-password`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Request Body:**
```json
{
  "newPassword": "NewSecurePass123!"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Admin password reset successfully"
}
```

**Use Case:** When admin forgets password or account needs recovery.

---

### 9. Unlock Admin Account

**Endpoint:** `POST /admin/admins/{adminId}/unlock`

**Authorization:** Bearer token required (ROLE_ADMIN, Level 0 or 1)

**Description:** Unlock admin account after multiple failed login attempts.

**Response (200):**
```json
{
  "id": 3,
  "username": "new_admin",
  ...
}
```

---

## IMAGE UPLOAD SYSTEM

**Authorization:** Level 0, 1, or 2 admins only

**Storage:** Cloudflare R2 (S3-compatible)

**Supported Formats:** JPG, JPEG, PNG, GIF, WEBP

**Max File Size:** Configurable in application.properties

### 1. Upload Profile Image

**Endpoint:** `POST /admin/image/profile`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Content-Type:** `multipart/form-data`

**Form Data:**
- `file` (required): Image file

**Success Response (200):**
```json
{
  "success": true,
  "message": "Profil resmi başarıyla yüklendi",
  "imageUrl": "https://cdn.example.com/admin/profile/1.jpg"
}
```

**Error Response (400):**
```json
{
  "success": false,
  "message": "Invalid file type. Allowed: JPG, PNG, GIF, WEBP"
}
```

**Frontend Implementation:**
```javascript
const uploadProfileImage = async (file) => {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await fetch('/api/v1/admin/image/profile', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`
    },
    body: formData
  });
  
  return await response.json();
};
```

---

### 2. Update Profile Image

**Endpoint:** `PUT /admin/image/profile`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Content-Type:** `multipart/form-data`

**Form Data:**
- `file` (required): New image file
- `oldImageUrl` (optional): URL of old image to delete

**Success Response (200):**
```json
{
  "success": true,
  "message": "Profil resmi başarıyla güncellendi",
  "imageUrl": "https://cdn.example.com/admin/profile/1-updated.jpg"
}
```

**Behavior:**
- Uploads new image
- Deletes old image if `oldImageUrl` provided
- Updates database profile picture URL

---

### 3. Delete Profile Image

**Endpoint:** `DELETE /admin/image/profile`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Query Parameters:**
- `imageUrl` (required): Full URL of image to delete

**Example:** `DELETE /admin/image/profile?imageUrl=https://cdn.example.com/admin/profile/1.jpg`

**Success Response (200):**
```json
{
  "success": true,
  "message": "Profil resmi başarıyla silindi"
}
```

---

### 4. Upload Book Cover

**Endpoint:** `POST /admin/image/book-cover/{bookId}`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Content-Type:** `multipart/form-data`

**Path Parameters:**
- `bookId` (required): Book ID

**Form Data:**
- `file` (required): Image file

**Success Response (200):**
```json
{
  "success": true,
  "message": "Kitap kapağı başarıyla yüklendi",
  "imageUrl": "https://cdn.example.com/books/123/cover.jpg"
}
```

---

### 5. Update Book Cover

**Endpoint:** `PUT /admin/image/book-cover/{bookId}`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Content-Type:** `multipart/form-data`

**Path Parameters:**
- `bookId` (required): Book ID

**Form Data:**
- `file` (required): New image file
- `oldImageUrl` (optional): URL of old image to delete

**Success Response (200):**
```json
{
  "success": true,
  "message": "Kitap kapağı başarıyla güncellendi",
  "imageUrl": "https://cdn.example.com/books/123/cover-updated.jpg"
}
```

---

### 6. Delete Book Cover

**Endpoint:** `DELETE /admin/image/book-cover`

**Authorization:** Bearer token required (ROLE_ADMIN)

**Query Parameters:**
- `imageUrl` (required): Full URL of image to delete

**Success Response (200):**
```json
{
  "success": true,
  "message": "Kitap kapağı başarıyla silindi"
}
```

---

## ADMIN LOGGING ENDPOINTS

**Authorization:** Level 0 (Super Admin) only

All logging endpoints follow similar patterns with pagination and filtering.

### Common Query Parameters
- `page` (default: 0) - Page number
- `size` (default: 20) - Items per page
- `sortBy` (default: createdAt) - Sort field
- `sortDirection` (default: desc) - Sort direction

### 1. Admin Activity Logs

**Base:** `/admin/activity-logs`

#### Get All Activity Logs

**Endpoint:** `GET /admin/activity-logs`

**Query Parameters:**
- `adminId` (optional): Filter by admin ID
- `action` (optional): Filter by action (READ, CREATE, UPDATE, DELETE)
- `resourceType` (optional): Filter by resource type
- `startDate` (optional): Filter by start date (ISO 8601)

**Example:** `GET /admin/activity-logs?adminId=1&action=CREATE&page=0&size=20`

**Response (200):**
```json
{
  "success": true,
  "data": {
    "logs": [
      {
        "id": 1,
        "adminId": 1,
        "adminUsername": "super_admin",
        "action": "CREATE",
        "resourceType": "ADMIN",
        "resourceId": 3,
        "details": "Created new admin with username: new_admin",
        "ipAddress": "192.168.1.100",
        "userAgent": "Mozilla/5.0...",
        "createdAt": "2026-02-12T11:00:00"
      }
    ],
    "currentPage": 0,
    "totalPages": 1,
    "totalItems": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

#### Get Activity Log by ID

**Endpoint:** `GET /admin/activity-logs/{logId}`

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "adminId": 1,
    "adminUsername": "super_admin",
    "action": "CREATE",
    "resourceType": "ADMIN",
    "resourceId": 3,
    "details": "Created new admin with username: new_admin",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0...",
    "createdAt": "2026-02-12T11:00:00"
  }
}
```

**Actions Logged:**
- READ - Viewing sensitive data
- CREATE - Creating new resources
- UPDATE - Updating existing resources
- DELETE - Deleting/deactivating resources

**Resource Types:**
- ADMIN
- USER
- TOKEN
- SETTINGS
- etc.

---

### 2. Authentication Error Logs

**Base:** `/admin/auth-error-logs`

#### Get All Auth Error Logs

**Endpoint:** `GET /admin/auth-error-logs`

**Query Parameters:**
- `userId` (optional): Filter by user ID
- `userType` (optional): Filter by user type
- `errorType` (optional): Filter by error type
- `ipAddress` (optional): Filter by IP address
- `startDate` (optional): Filter by start date (ISO 8601)

**Response (200):**
```json
{
  "success": true,
  "data": {
    "logs": [
      {
        "id": 1,
        "userId": 5,
        "username": "johndoe",
        "userType": "client",
        "errorType": "INVALID_CREDENTIALS",
        "errorMessage": "Invalid username or password",
        "ipAddress": "192.168.1.200",
        "userAgent": "Mozilla/5.0...",
        "attemptedAt": "2026-02-12T10:45:00"
      }
    ],
    "currentPage": 0,
    "totalPages": 1,
    "totalItems": 1
  }
}
```

#### Get Auth Error Log by ID

**Endpoint:** `GET /admin/auth-error-logs/{id}`

#### Get Logs by User ID

**Endpoint:** `GET /admin/auth-error-logs/user/{userId}`

#### Get Logs by IP Address

**Endpoint:** `GET /admin/auth-error-logs/ip/{ipAddress}`

#### Get Statistics

**Endpoint:** `GET /admin/auth-error-logs/statistics`

**Response (200):**
```json
{
  "success": true,
  "data": {
    "totalErrors": 150,
    "last24Hours": 12,
    "last7Days": 45,
    "last30Days": 150,
    "byErrorType": {
      "INVALID_CREDENTIALS": 100,
      "ACCOUNT_LOCKED": 25,
      "EMAIL_NOT_VERIFIED": 25
    },
    "byUserType": {
      "client": 80,
      "coach": 50,
      "admin": 20
    }
  }
}
```

---

### 3. Sensitive Endpoint Access Logs

**Base:** `/admin/sensitive-access-logs`

**Description:** Logs all access to sensitive endpoints (admin management, logs, tokens, etc.)

#### Get All Sensitive Access Logs

**Endpoint:** `GET /admin/sensitive-access-logs`

**Query Parameters:**
- `userId` (optional): Filter by user ID
- `userType` (optional): Filter by user type
- `severity` (optional): Filter by severity (LOW, MEDIUM, HIGH, CRITICAL)
- `category` (optional): Filter by category (ADMIN_MANAGEMENT, TOKEN_MANAGEMENT, etc.)
- `ipAddress` (optional): Filter by IP address
- `startDate` (optional): Filter by start date (ISO 8601)

**Response (200):**
```json
{
  "success": true,
  "data": {
    "logs": [
      {
        "id": 1,
        "userId": 1,
        "username": "super_admin",
        "userType": "admin",
        "endpoint": "/api/v1/admin/admins",
        "method": "POST",
        "severity": "HIGH",
        "category": "ADMIN_MANAGEMENT",
        "ipAddress": "192.168.1.100",
        "userAgent": "Mozilla/5.0...",
        "accessedAt": "2026-02-12T11:00:00",
        "responseStatus": 201
      }
    ],
    "currentPage": 0,
    "totalPages": 1,
    "totalItems": 1
  }
}
```

#### Get Sensitive Access Log by ID

**Endpoint:** `GET /admin/sensitive-access-logs/{id}`

#### Get Logs by User ID

**Endpoint:** `GET /admin/sensitive-access-logs/user/{userId}`

#### Get Logs by IP Address

**Endpoint:** `GET /admin/sensitive-access-logs/ip/{ipAddress}`

#### Get Statistics

**Endpoint:** `GET /admin/sensitive-access-logs/statistics`

**Response (200):**
```json
{
  "success": true,
  "data": {
    "totalAccesses": 500,
    "last24Hours": 45,
    "last7Days": 180,
    "last30Days": 500,
    "bySeverity": {
      "LOW": 200,
      "MEDIUM": 150,
      "HIGH": 100,
      "CRITICAL": 50
    },
    "byCategory": {
      "ADMIN_MANAGEMENT": 150,
      "TOKEN_MANAGEMENT": 100,
      "LOG_ACCESS": 80,
      "USER_MANAGEMENT": 170
    }
  }
}
```

**Severity Levels:**
- LOW - Read operations on non-critical data
- MEDIUM - Update operations
- HIGH - Create/Delete operations, password resets
- CRITICAL - Token management, system configuration changes

---

### 4. User Activity Logs

**Base:** `/admin/user-activity-logs`

**Description:** Logs all user (client/coach) activities for auditing.

#### Get All User Activity Logs

**Endpoint:** `GET /admin/user-activity-logs`

**Query Parameters:**
- `userId` (optional): Filter by user ID
- `userType` (optional): Filter by user type (client/coach)
- `action` (optional): Filter by action (LOGIN, REGISTER, LOGOUT, etc.)
- `resourceType` (optional): Filter by resource type
- `success` (optional): Filter by success status (true/false)
- `startDate` (optional): Filter by start date (ISO 8601)
- `endDate` (optional): Filter by end date (ISO 8601)
- `ipAddress` (optional): Filter by IP address

**Response (200):**
```json
{
  "success": true,
  "data": {
    "logs": [
      {
        "id": 1,
        "userId": 5,
        "username": "johndoe",
        "userType": "client",
        "action": "LOGIN",
        "resourceType": null,
        "resourceId": null,
        "details": "User logged in successfully",
        "success": true,
        "ipAddress": "192.168.1.200",
        "userAgent": "Mozilla/5.0...",
        "createdAt": "2026-02-12T10:30:00"
      }
    ],
    "currentPage": 0,
    "totalPages": 1,
    "totalItems": 1
  }
}
```

#### Get User Activity Log by ID

**Endpoint:** `GET /admin/user-activity-logs/{logId}`

#### Get Logs for Specific User

**Endpoint:** `GET /admin/user-activity-logs/user/{userId}`

**Query Parameters:**
- `userType` (default: client): User type (client/coach)

**Common Actions Logged:**
- LOGIN - User login
- REGISTER - New user registration
- LOGOUT - User logout
- EMAIL_VERIFIED - Email verification completed
- PASSWORD_CHANGED - Password changed
- PASSWORD_RESET - Password reset requested/completed
- PROFILE_UPDATED - Profile information updated
- PROFILE_IMAGE_UPLOADED - Profile picture uploaded

---

### 5. Token Management

**Base:** `/admin/tokens`

**Description:** View and manage verification and password reset tokens.

#### Get All Password Reset Tokens

**Endpoint:** `GET /admin/tokens/password-reset`

**Query Parameters:**
- `userType` (optional): Filter by user type
- `includeExpired` (default: true): Include expired tokens
- `page`, `size`, `sortBy`, `sortDirection`

**Response (200):**
```json
{
  "success": true,
  "data": {
    "tokens": [
      {
        "id": 1,
        "userId": 5,
        "username": "johndoe",
        "userType": "client",
        "token": "a4f2e9b8...",
        "expiryDate": "2026-02-13T10:30:00",
        "used": false,
        "createdAt": "2026-02-12T10:30:00"
      }
    ],
    "currentPage": 0,
    "totalPages": 1,
    "totalItems": 1
  }
}
```

#### Get Password Reset Token by ID

**Endpoint:** `GET /admin/tokens/password-reset/{tokenId}`

#### Delete Password Reset Token

**Endpoint:** `DELETE /admin/tokens/password-reset/{tokenId}`

**Response (200):**
```json
{
  "success": true,
  "message": "Password reset token deleted successfully"
}
```

#### Get All Verification Tokens

**Endpoint:** `GET /admin/tokens/verification`

**Query Parameters:** Same as password reset tokens

**Response:** Similar structure to password reset tokens

#### Get Verification Token by ID

**Endpoint:** `GET /admin/tokens/verification/{tokenId}`

#### Delete Verification Token

**Endpoint:** `DELETE /admin/tokens/verification/{tokenId}`

---

### 6. Refresh Token Management

**Base:** `/admin/refresh-tokens`

**Description:** View and manage refresh tokens for all users.

#### Get All Refresh Tokens

**Endpoint:** `GET /admin/refresh-tokens`

**Query Parameters:**
- `userType` (optional): Filter by user type
- `userId` (optional): Filter by user ID
- `isRevoked` (optional): Filter by revoked status
- `ipAddress` (optional): Filter by IP address

**Response (200):**
```json
[
  {
    "id": 1,
    "userId": 5,
    "userType": "client",
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "isRevoked": false,
    "expiryDate": "2026-03-14T10:30:00",
    "createdAt": "2026-02-12T10:30:00",
    "ipAddress": "192.168.1.200",
    "deviceInfo": "Mozilla/5.0..."
  }
]
```

#### Get Refresh Token by ID

**Endpoint:** `GET /admin/refresh-tokens/{id}`

#### Get Active Tokens for User

**Endpoint:** `GET /admin/refresh-tokens/user/{userId}`

**Query Parameters:**
- `userType` (required): User type

#### Get Token Statistics

**Endpoint:** `GET /admin/refresh-tokens/stats`

**Response (200):**
```json
{
  "totalTokens": 250,
  "activeTokens": 180,
  "revokedTokens": 50,
  "expiredTokens": 20,
  "byUserType": {
    "client": 150,
    "coach": 80,
    "admin": 20
  }
}
```

#### Revoke Refresh Token

**Endpoint:** `PUT /admin/refresh-tokens/{id}/revoke`

**Response (200):**
```json
{
  "message": "Refresh token revoked successfully"
}
```

#### Revoke All User Tokens

**Endpoint:** `PUT /admin/refresh-tokens/revoke-all`

**Query Parameters:**
- `userId` (required): User ID
- `userType` (required): User type

**Response (200):**
```json
{
  "message": "All tokens revoked for user",
  "revokedCount": 3
}
```

#### Delete Refresh Token

**Endpoint:** `DELETE /admin/refresh-tokens/{id}`

**Description:** Permanently delete token (not just revoke).

**Response (200):**
```json
{
  "message": "Refresh token deleted successfully"
}
```

#### Trigger Manual Cleanup

**Endpoint:** `POST /admin/refresh-tokens/cleanup`

**Description:** Manually trigger cleanup of expired and revoked tokens.

**Response (200):**
```json
{
  "message": "Token cleanup completed",
  "tokensRemoved": 15
}
```

---

## DATABASE BACKUP

**Base:** `/admin/database-backup`

**Authorization:** Level 0 (Super Admin) only

### 1. Create Manual Backup

**Endpoint:** `POST /admin/database-backup/create`

**Description:** Manually trigger database backup. Backup is created and emailed in background.

**Response (200):**
```json
{
  "success": true,
  "message": "Database backup process started successfully",
  "note": "The backup will be created and emailed in the background"
}
```

**Behavior:**
- Backup runs asynchronously (non-blocking)
- MySQL database is dumped to SQL file
- File is compressed
- Email sent to configured admin email with backup attached
- Also scheduled automatically daily at 3:00 AM

---

### 2. Get Backup Status

**Endpoint:** `GET /admin/database-backup/status`

**Response (200):**
```json
{
  "success": true,
  "status": "configured",
  "scheduledTime": "Daily at 3:00 AM",
  "manualTrigger": "Available via /create endpoint"
}
```

---

## ERROR HANDLING

### Standard Error Response Format

All error responses follow a consistent structure:

```json
{
  "success": false,
  "message": "Error description",
  "error": "ERROR_CODE", // Optional
  "timestamp": "2026-02-12T10:30:00"
}
```

### HTTP Status Codes

| Code | Meaning | Use Case |
|------|---------|----------|
| 200 | OK | Successful request |
| 201 | Created | Resource created successfully |
| 202 | Accepted | Request accepted (e.g., 2FA required) |
| 400 | Bad Request | Invalid input, validation error |
| 401 | Unauthorized | Authentication required or failed |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Duplicate resource (username/email exists) |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

### Common Error Messages

#### Authentication Errors
```json
{
  "success": false,
  "message": "Invalid username or password"
}
```

#### Authorization Errors
```json
{
  "success": false,
  "message": "Access denied. Insufficient permissions."
}
```

#### Validation Errors
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "username": "Username must be between 3 and 50 characters",
    "email": "Invalid email format"
  }
}
```

#### Token Errors
```json
{
  "error": "Invalid or expired refresh token. Please login again."
}
```

#### CAPTCHA Errors
```json
{
  "error": "CAPTCHA verification failed. Please try again.",
  "captchaRequired": true
}
```

#### Rate Limit Errors
```json
{
  "error": "Too many requests. Please try again later.",
  "retryAfter": 60 // seconds
}
```

### Frontend Error Handling Strategy

```javascript
const handleApiError = (error) => {
  if (error.response) {
    const { status, data } = error.response;
    
    switch (status) {
      case 400:
        // Validation errors - show to user
        showValidationErrors(data.errors || data.message);
        break;
      
      case 401:
        // Unauthorized - attempt token refresh or logout
        if (!error.config._retry) {
          return refreshTokenAndRetry(error.config);
        } else {
          logout();
          redirectToLogin();
        }
        break;
      
      case 403:
        // Forbidden - show permission error
        showError('You do not have permission to perform this action');
        break;
      
      case 404:
        // Not found
        showError('Resource not found');
        break;
      
      case 429:
        // Rate limited
        const retryAfter = data.retryAfter || 60;
        showError(`Too many requests. Please wait ${retryAfter} seconds.`);
        break;
      
      case 500:
        // Server error
        showError('Server error. Please try again later.');
        logErrorToMonitoring(error);
        break;
      
      default:
        showError(data.message || 'An unexpected error occurred');
    }
  } else if (error.request) {
    // Network error
    showError('Network error. Please check your connection.');
  } else {
    // Other errors
    showError('An unexpected error occurred');
  }
};
```

---

## RATE LIMITING

The API implements rate limiting to prevent abuse.

### Rate Limit Headers

Responses include rate limit information in headers:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
X-RateLimit-Reset: 1707739200
```

### Rate Limits by Endpoint Category

| Category | Limit | Window |
|----------|-------|--------|
| Login | 5 attempts | 15 minutes |
| Register | 3 attempts | 1 hour |
| Password Reset | 3 attempts | 1 hour |
| Email Verification | 5 attempts | 1 hour |
| General API | 100 requests | 15 minutes |
| Admin Operations | 200 requests | 15 minutes |

### Rate Limit Exceeded Response

**Status:** 429 Too Many Requests

```json
{
  "error": "Too many requests. Please try again later.",
  "retryAfter": 900 // seconds until reset
}
```

### Frontend Handling

```javascript
// Calculate retry time from header
const retryAfter = response.headers['x-ratelimit-reset'];
const now = Math.floor(Date.now() / 1000);
const waitSeconds = retryAfter - now;

// Show countdown to user
showRateLimitMessage(`Please wait ${waitSeconds} seconds before retrying`);

// Auto-retry after wait period
setTimeout(() => {
  retryRequest();
}, waitSeconds * 1000);
```

---

## REQUEST/RESPONSE PATTERNS

### Authentication Flow Headers

#### Request
```
POST /api/v1/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json
User-Agent: MyApp/1.0.0
```

#### Response
```
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: refreshToken=550e8400...; HttpOnly; Secure; SameSite=Strict; Max-Age=2592000
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 4
X-RateLimit-Reset: 1707739200
```

### Authenticated Request Headers

```
GET /api/v1/admin/profile HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json
Accept: application/json
```

### Pagination Response Pattern

All paginated endpoints follow this structure:

```json
{
  "items": [...], // Array name varies (admins, logs, etc.)
  "currentPage": 0,
  "totalPages": 5,
  "totalItems": 95,
  "hasNext": true,
  "hasPrevious": false
}
```

### Filtering Query Parameters

Most list endpoints support filtering:

```
GET /api/v1/admin/admins?filter=value&page=0&size=20
GET /api/v1/admin/activity-logs?adminId=1&action=CREATE&startDate=2026-02-01T00:00:00
```

### Date Format

All dates use ISO 8601 format:

```
2026-02-12T10:30:00
2026-02-12T10:30:00Z // UTC
2026-02-12T10:30:00+02:00 // With timezone
```

---

## BEST PRACTICES

### 1. Token Management

✅ **DO:**
- Store access token in memory or session storage
- Store refresh token in HTTP-only cookie (web) or secure storage (mobile)
- Implement automatic token refresh on 401 errors
- Clear all tokens on logout
- Implement token refresh before expiration (e.g., 1 minute before)

❌ **DON'T:**
- Store tokens in localStorage (XSS vulnerability)
- Store tokens in cookies accessible by JavaScript
- Send tokens in URL parameters
- Log tokens in console or error messages

### 2. Security Headers

Always include:
- `Authorization: Bearer {token}` for authenticated requests
- `Content-Type: application/json`
- User-Agent with app version for tracking

### 3. Error Recovery

Implement retry logic with exponential backoff:

```javascript
const retryRequest = async (config, maxRetries = 3) => {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await axios(config);
    } catch (error) {
      if (i === maxRetries - 1) throw error;
      
      // Don't retry on 4xx errors (except 401)
      if (error.response?.status >= 400 && 
          error.response?.status < 500 && 
          error.response?.status !== 401) {
        throw error;
      }
      
      // Exponential backoff
      const delay = Math.pow(2, i) * 1000;
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
};
```

### 4. State Management

```javascript
// Centralized auth state
const authState = {
  accessToken: null,
  user: null,
  isAuthenticated: false,
  isLoading: false,
  requires2FA: false
};

// Load user session on app start
const initializeAuth = async () => {
  try {
    authState.isLoading = true;
    
    // Try to refresh token (uses cookie)
    const response = await api.post('/auth/refresh');
    authState.accessToken = response.data.accessToken;
    
    // Get user info
    const userResponse = await api.get('/auth/me');
    authState.user = userResponse.data;
    authState.isAuthenticated = true;
  } catch (error) {
    // Not logged in or token expired
    authState.isAuthenticated = false;
  } finally {
    authState.isLoading = false;
  }
};
```

### 5. Admin Level Checks

Always check admin level before showing UI elements:

```javascript
const canAccessAdminManagement = (user) => {
  return user?.userType === 'admin' && 
         user?.level !== undefined && 
         user?.level <= 1; // Level 0 or 1
};

const canAccessLogs = (user) => {
  return user?.userType === 'admin' && 
         user?.level === 0; // Level 0 only
};

const canAccessProfile = (user) => {
  return user?.userType === 'admin'; // All admin levels
};
```

### 6. File Upload Handling

```javascript
const uploadImage = async (file, endpoint) => {
  // Validate file on frontend
  const validTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  if (!validTypes.includes(file.type)) {
    throw new Error('Invalid file type');
  }
  
  const maxSize = 5 * 1024 * 1024; // 5MB
  if (file.size > maxSize) {
    throw new Error('File too large');
  }
  
  // Create form data
  const formData = new FormData();
  formData.append('file', file);
  
  // Upload with progress
  return axios.post(endpoint, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      'Authorization': `Bearer ${accessToken}`
    },
    onUploadProgress: (progressEvent) => {
      const percentCompleted = Math.round(
        (progressEvent.loaded * 100) / progressEvent.total
      );
      updateProgress(percentCompleted);
    }
  });
};
```

### 7. Logout Handling

```javascript
const logout = async (logoutAll = false) => {
  try {
    // Call appropriate logout endpoint
    const endpoint = logoutAll ? '/auth/logout-all' : '/auth/logout';
    await api.post(endpoint);
  } catch (error) {
    console.error('Logout error:', error);
  } finally {
    // Clear state regardless of API response
    clearAccessToken();
    clearRefreshToken(); // Mobile only
    clearUserState();
    redirectToLogin();
  }
};
```

### 8. CAPTCHA Integration

```javascript
// Load reCAPTCHA script
<script src="https://www.google.com/recaptcha/api.js" async defer></script>

// In login form for admin
const handleAdminLogin = async (credentials) => {
  if (credentials.userType === 'admin') {
    // Execute reCAPTCHA
    const captchaToken = await grecaptcha.execute(
      CAPTCHA_SITE_KEY, 
      { action: 'admin_login' }
    );
    
    credentials.captchaToken = captchaToken;
  }
  
  await login(credentials);
};
```

### 9. 2FA Flow Implementation

```javascript
const handle2FALogin = async (username, password, userType) => {
  try {
    const response = await api.post('/auth/login', {
      username,
      password,
      userType,
      captchaToken: userType === 'admin' ? await getCaptchaToken() : null
    });
    
    if (response.status === 202) {
      // 2FA required
      set2FARequired(true);
      setUsername(response.data.username);
      showVerificationCodeInput();
    } else {
      // Standard login success
      handleLoginSuccess(response.data);
    }
  } catch (error) {
    handleLoginError(error);
  }
};

const verify2FACode = async (username, code) => {
  try {
    const response = await api.post('/admin/2fa/verify-login', {
      username,
      code
    });
    
    handleLoginSuccess(response.data);
  } catch (error) {
    showError('Invalid verification code');
  }
};
```

### 10. Logging Best Practices

For debugging, implement structured logging:

```javascript
const logger = {
  info: (message, data) => {
    if (process.env.NODE_ENV === 'development') {
      console.log(`[INFO] ${message}`, data);
    }
  },
  
  error: (message, error) => {
    console.error(`[ERROR] ${message}`, {
      message: error.message,
      status: error.response?.status,
      // Never log tokens or sensitive data
      endpoint: error.config?.url
    });
    
    // Send to error monitoring service
    errorMonitoring.capture(error);
  }
};
```

---

## APPENDIX

### User Types Reference

```javascript
const USER_TYPES = {
  ADMIN: 'admin',
  CLIENT: 'client',
  COACH: 'coach'
};
```

### Admin Levels Reference

```javascript
const ADMIN_LEVELS = {
  SUPER_ADMIN: 0,    // Full access
  ADMIN: 1,          // Admin management
  BASIC_ADMIN: 2     // Self-management only
};
```

### Action Types (Activity Logs)

```javascript
const LOG_ACTIONS = {
  READ: 'READ',
  CREATE: 'CREATE',
  UPDATE: 'UPDATE',
  DELETE: 'DELETE',
  LOGIN: 'LOGIN',
  LOGOUT: 'LOGOUT',
  REGISTER: 'REGISTER'
};
```

### Severity Levels (Sensitive Access)

```javascript
const SEVERITY_LEVELS = {
  LOW: 'LOW',
  MEDIUM: 'MEDIUM',
  HIGH: 'HIGH',
  CRITICAL: 'CRITICAL'
};
```

### Complete Endpoint Summary

#### Public Endpoints (No Authentication)
- `POST /auth/register`
- `POST /auth/login`
- `GET /auth/verify-email`
- `POST /auth/resend-verification`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`
- `GET /auth/health`
- `POST /admin/2fa/verify-login`

#### Authenticated User Endpoints
- `GET /auth/me`
- `POST /auth/verify-password`
- `POST /auth/refresh`
- `POST /auth/logout`
- `POST /auth/logout-all`

#### Admin Endpoints (All Levels)
- `GET /admin/profile`
- `PUT /admin/profile`
- `POST /admin/profile/change-password`
- `POST /admin/image/profile`
- `PUT /admin/image/profile`
- `DELETE /admin/image/profile`
- `POST /admin/image/book-cover/{bookId}`
- `PUT /admin/image/book-cover/{bookId}`
- `DELETE /admin/image/book-cover`
- `POST /admin/2fa/setup`
- `POST /admin/2fa/verify`
- `POST /admin/2fa/disable`
- `GET /admin/2fa/status`

#### Admin Endpoints (Level 0 or 1)
- `GET /admin/admins`
- `GET /admin/admins/{adminId}`
- `POST /admin/admins`
- `PUT /admin/admins/{adminId}`
- `DELETE /admin/admins/{adminId}`
- `POST /admin/admins/{adminId}/activate`
- `POST /admin/admins/{adminId}/deactivate`
- `POST /admin/admins/{adminId}/reset-password`
- `POST /admin/admins/{adminId}/unlock`
- `GET /admin/profile/{adminId}`
- `POST /admin/profile/{adminId}/deactivate`
- `POST /admin/profile/{adminId}/reactivate`

#### Admin Endpoints (Level 0 Only)
- All `/admin/activity-logs` endpoints
- All `/admin/auth-error-logs` endpoints
- All `/admin/sensitive-access-logs` endpoints
- All `/admin/user-activity-logs` endpoints
- All `/admin/tokens` endpoints
- All `/admin/refresh-tokens` endpoints
- All `/admin/database-backup` endpoints

---

## SUPPORT & DOCUMENTATION

### Additional Resources
- Check individual documentation files in `/docs` folder:
  - `19.2FA_IMPLEMENTATION.md` - Detailed 2FA implementation
  - `26.0REFRESH_TOKEN_SUMMARY.md` - Refresh token system details
  - `26.2REFRESH_TOKEN_FRONTEND_MIGRATION.md` - Frontend token migration guide
  - `8IMAGE_UPLOAD_SYSTEM.md` - Image upload implementation
  - `9EMAIL_NOTIFICATION_SYSTEM.md` - Email system details

### Postman Collections
All endpoints are documented in Postman collections in `/postman_files`:
- `0auth.postman_collection.json`
- `11image_upload.postman_collection.json`
- `13profile_management.postman_collection.json`
- `19admin_admin_management.postman_collection.json`
- `22admin_activity_logs.postman_collection.json`
- `23auth_error_logs.postman_collection.json`
- `24sensitive_access_logs.postman_collection.json`
- `25user_activity_logs.postman_collection.json`
- `26.1_refresh_token_user.postman_collection.json`
- `26.2_refresh_token_admin.postman_collection.json`

---

**Last Updated:** February 12, 2026  
**API Version:** 1.0  
**Spring Boot Version:** 3.4.0  
**Java Version:** 21
