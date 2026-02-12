# Template Java Spring Boot REST API

A production-ready Spring Boot REST API template to kickstart your projects. Built with security, scalability, and developer experience in mind.

## 🚀 Features

### Authentication & Security
- **JWT Authentication** - Access & refresh token system with token rotation for enhanced security
- **Refresh Token Management** - 30-day refresh tokens with automatic rotation and reuse detection
- **Argon2id Password Hashing** - Industry-standard password hashing with pepper & salt
- **Two-Factor Authentication (2FA)** - TOTP-based 2FA compatible with Google Authenticator, Authy, etc.
- **Google reCAPTCHA** - Bot protection for admin login endpoints
- **Rate Limiting** - Configurable rate limits for login, API calls, and email verification
- **Security Headers** - HSTS, X-Frame-Options, Referrer-Policy, and more
- **Session Management** - Logout from single or all devices

### User Management
- **Multi-User Type Support** - Admin, Client, Coach user types (easily customizable)
- **Email Verification** - User email verification with customizable HTML templates
- **Password Reset** - Secure password reset flow with email notifications
- **Admin Management** - Full admin CRUD operations with activity logging
- **Admin Levels** - Hierarchical admin permission system (Level 0, 1, 2)
- **Admin Profile Management** - Complete profile CRUD with password change

### Cloud & Storage
- **Cloudflare R2 Integration** - S3-compatible object storage for images/files
- **Image Upload Service** - Profile pictures, book covers, with validation
- **Multi-format Support** - JPG, PNG, GIF, WEBP

### Monitoring & Logging
- **Admin Activity Logging** - Track all admin actions (CRUD operations) with full audit trail
- **Authentication Error Logging** - Log failed login attempts with IP, User-Agent, and error types
- **Sensitive Endpoint Access Logging** - Track access to sensitive endpoints with severity levels
- **User Activity Logging** - Complete user action tracking for clients and coaches
- **Token Management Logging** - Track verification and password reset token lifecycle
- **Database Backup** - Automated MySQL backup with email notifications (daily at 3:00 AM)

### Developer Experience
- **Frontend Developer Guide** - Comprehensive API guide for frontend developers (700+ lines)
- **Postman Collections** - Complete endpoint documentation with examples
- **Environment Variables** - Full `.env` support via spring-dotenv
- **Global Exception Handling** - Consistent error responses
- **DTO Pattern** - Clean separation of concerns
- **Async Processing** - Non-blocking activity logging
- **Extensive Documentation** - Feature-specific docs for all major systems

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| Framework | Spring Boot 3.4.0 |
| Language | Java 21 |
| Database | MySQL with HikariCP |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security 6 |
| Password Hashing | Argon2id (argon2-jvm) |
| JWT | jjwt (io.jsonwebtoken) |
| 2FA | Google Authenticator (TOTP) |
| QR Code | ZXing |
| Object Storage | AWS SDK (S3-compatible for Cloudflare R2) |
| Email | Spring Mail |
| Templating | Thymeleaf |
| JSON | Jackson |
| Utility | Lombok |

## 📁 Project Structure

```
src/main/java/com/g4stly/templateApp/
├── config/                 # Configuration classes
│   ├── ApplicationConfig.java
│   ├── AsyncConfig.java
│   ├── CloudflareR2Config.java
│   ├── CorsConfig.java
│   ├── JwtConfig.java
│   ├── MailConfig.java
│   ├── RateLimitConfig.java
│   ├── ScheduledTasks.java
│   └── SecurityConfig.java
├── controllers/            # REST API endpoints
│   ├── AuthController.java
│   ├── RefreshTokenController.java
│   ├── TwoFactorAuthController.java
│   ├── AdminManagementController.java
│   ├── AdminProfileController.java
│   ├── AdminActivityLogController.java
│   ├── AdminAuthErrorController.java
│   ├── AdminSensitiveAccessController.java
│   ├── AdminUserActivityLogController.java
│   ├── AdminImageController.java
│   ├── AdminTokenManagementController.java
│   ├── AdminRefreshTokenController.java
│   └── DatabaseBackupController.java
├── dto/                    # Data Transfer Objects
│   ├── admin/
│   ├── auth/
│   ├── profile/
│   └── two_factor/
├── exception/              # Custom exceptions & handlers
│   ├── GlobalExceptionHandler.java
│   ├── BadRequestException.java
│   ├── UnauthorizedException.java
│   ├── ForbiddenException.java
│   ├── RefreshToken.java
│   ├── AdminActivityLog.java
│   ├── AuthenticationErrorLog.java
│   ├── SensitiveEndpointAccessLog.java
│   ├── UserActivityeption.java
├── models/                 # JPA Entities
│   ├── Admin.java
│   ├── AdminActivityLog.java
│   ├── AuthenticationErrorLog.java
│   ├── PasswordResetToken.java
│   ├── VerificationToken.java
│   └── UserType.java
├── repos/                  # Spring Data Repositories
├── security/               # Security components
│   ├── JwtAuthFilter.java
│   ├── JwtAuthEntryPoint.java
│   ├── JwtUtils.java
│   └── RefreshTokenService.java
│   ├── PasswordService.java
│   ├── TwoFactorAuthService.java
│   ├── EmailService.java
│   ├── RateLimitService.java
│   ├── ImageUploadService.java
│   ├── DatabaseBackupService.java
│   ├── AdminManagementService.java
│   ├── AdminProfileService.java
│   ├── AdminActivityLogService.java
│   ├── AdminAuthErrorService.java
│   ├── AdminSensitiveAccessService.java
│   ├── UserActivityLogService.java
│   ├── AdminTokenManagementService.java
│   └── CaptchaService.javaabaseBackupService.java
│   ├── AdminManagementService.java
│   ├── AdminActivityLogger.java
│   └── ...
└── utils/                  # Utility classes

src/main/resources/
├── application.properties  # Configuration
└── templates/              # Email templates
    ├── verification-email.html
    ├── password-reset-email.html
    ├── password-reset-success-email.html
    └── ...
```

## ⚙️ Configuration

### Environment Variables

Create a `.env` file in the project root:

```env
# Application
APP_NAME=Your App Name
SERVER_PORT=8080

# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=your_database
DB_USER=root
DB_PASSWORD=your_password
DDL_AUTO=update

# Security
PEPPER=your-secure-pepper-string
JWT_SECRET=your-256-bit-secret-key-for-jwt-signing
JWT_EXPIRATION_MS=3600000
JWT_REFRESH_EXPIRATION_MS=604800000

# Argon2 Password Hashing (optional - has defaults)
ARGON2_MEMORY_COST=65536
ARGON2_TIME_COST=3
ARGON2_PARALLELISM=4

# Cloudflare R2 Storage
CLOUDFLARE_R2_ACCESS_KEY=your-access-key
CLOUDFLARE_R2_SECRET_KEY=your-secret-key
CLOUDFLARE_R2_ACCOUNT_ID=your-account-id
CLOUDFLARE_R2_BUCKET_NAME=your-bucket-name
CLOUDFLARE_R2_PUBLIC_DOMAIN=https://your-r2-domain.com

# Email (SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Frontend URL (for email links)
FRONTEND_URL=http://localhost:3000

# reCAPTCHA (optional)
RECAPTCHA_ENABLED=false
RECAPTCHA_SITE_KEY=your-site-key
RECAPTCHA_SECRET_KEY=your-secret-key

# Rate Limiting (optional - has defaults)
RATE_LIMITDocumentation

### 📖 Complete Frontend Developer Guide
**[`docs/0FRONTEND_GUIDE.md`](docs/0FRONTEND_GUIDE.md)** - Comprehensive 700+ line guide covering:
- All authentication flows (login, register, 2FA, password reset)
- Token management (refresh tokens, logout, multi-device)
- Complete endpoint reference with request/response examples
- Authorization rules and admin levels
- Error handling patterns
- JavaScript code examples for common implementations
- Security best practices
- Rate limiting details

### 🔌 API Endpoints Overview

#### Authentication & Session Management
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/auth/register` | Register new user | No |
| POST | `/api/v1/auth/login` | User login (returns 202 if 2FA required) | No |
| GET | `/api/v1/auth/me` | Get current user session info | Yes |
| POST | `/api/v1/auth/refresh` | Refresh access token | No (uses refresh token) |
| POST | `/api/v1/auth/logout` | Logout from current device | No (uses refresh token) |
| POST | `/api/v1/auth/logout-all` | Logout from all devices | Yes |
| POST | `/api/v1/auth/verify-password` | Verify password before change | No |
| POST | `/api/v1/auth/forgot-password` | Request password reset | No |
| POST | `/api/v1/auth/reset-password` | Reset password with token | No |
| GET | `/api/v1/auth/verify-email` | Verify email address | No |
| POST | `/api/v1/auth/resend-verification` | Resend verification email | No |
| GET | `/api/v1/auth/health` | Health check | No |

#### Two-Factor Authentication (Admin Only)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/admin/2fa/setup` | Generate 2FA secret & QR code | Yes (Admin) |
| POST | `/api/v1/admin/2fa/verify` | Verify & enable 2FA | Yes (Admin) |
| POST | `/api/v1/admin/2fa/verify-login` | Complete 2FA login | No |
| POST | `/api/v1/admin/2fa/disable` | Disable 2FA | Yes (Admin) |
| GET | `/api/v1/admin/2fa/status` | Check 2FA status | Yes (Admin) |

#### Admin Profile (All Admin Levels)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/v1/admin/profile` | Get own profile | Yes (Admin) |
| GET | `/api/v1/admin/profile/{id}` | Get admin profile by ID | Yes (Admin L0/L1) |
| PUT | `/api/v1/admin/profile` | Update own profile | Yes (Admin) |
| POST |& Token System
- **Access Tokens**: Short-lived (15 minutes), includes userId, userType, adminLevel
- **Refresh Tokens**: Long-lived (30 days) with automatic rotation
- **Token Rotation**: New refresh token issued on each refresh (old token revoked)
- **Reuse Detection**: Automatic revocation if refresh token reused
- **Multi-Device Support**: Track all active sessions per user
- **Session Management**: Logout from single device or all devices

### Two-Factor Authentication
- TOTP-based (compatible with Google Authenticator, Authy, etc.)
- QR code generation for easy setup
- Manual entry option for secret key
- Admin-only feature (can be extended to other user types)
📘 Documentation

### Feature-Specific Guides
Detailed documentation available in the `docs/` folder:

- **[0FRONTEND_GUIDE.md](docs/0FRONTEND_GUIDE.md)** - Complete frontend developer guide (700+ lines)
- **[8IMAGE_UPLOAD_SYSTEM.md](docs/8IMAGE_UPLOAD_SYSTEM.md)** - Image upload implementation details
- **[9EMAIL_NOTIFICATION_SYSTEM.md](docs/9EMAIL_NOTIFICATION_SYSTEM.md)** - Email notification system
- **[13PROFILE_MANAGEMENT_SYSTEM.md](docs/13PROFILE_MANAGEMENT_SYSTEM.md)** - Profile management
- **[19.2FA_IMPLEMENTATION.md](docs/19.2FA_IMPLEMENTATION.md)** - Two-factor authentication guide
- **[20ADMIN_ACTIVITY_LOGS_TOKEN_CLEANUP.md](docs/20ADMIN_ACTIVITY_LOGS_TOKEN_CLEANUP.md)** - Activity logging
- **[21AUTHENTICATION_ERROR_LOGGING.md](docs/21AUTHENTICATION_ERROR_LOGGING.md)** - Auth error logging
- **[24SENSITIVE_ENDPOINT_ACCESS_LOGGING.md](docs/24SENSITIVE_ENDPOINT_ACCESS_LOGGING.md)** - Sensitive access logging
- **[25USER_ACTIVITY_LOGGING.md](docs/25USER_ACTIVITY_LOGGING.md)** - User activity logging
- **[26.0REFRESH_TOKEN_SUMMARY.md](docs/26.0REFRESH_TOKEN_SUMMARY.md)** - Refresh token summary
- **[26.1REFRESH_TOKEN_IMPLEMENTATION_PLAN.md](docs/26.1REFRESH_TOKEN_IMPLEMENTATION_PLAN.md)** - Refresh token implementation
- **[26.2REFRESH_TOKEN_FRONTEND_MIGRATION.md](docs/26.2REFRESH_TOKEN_FRONTEND_MIGRATION.md)** - Frontend token migration

## 🎨 Customization

### � Getting Started

### Prerequisites
- Java 21
- MySQL 8.0+
- Maven 3.6+

### Quick Start
1. Clone the repository
   ```bash
   git clone <repository-url>
   cd templateJavaApp
   ```

2. Create `.env` file with your configuration (see Configuration section)

3. Create MySQL database
   ```sql
   CREATE DATABASE your_database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

4. Run the application
   ```bash
   mvn spring-boot:run
   ```

5. API will be available at `http://localhost:8080/api/v1`

### First Steps
1. Register a user via `/api/v1/auth/register`
2. Verify email via link sent to email
3. Login via `/api/v1/auth/login`
4. Create admin users directly in database or via admin management endpoints

### Testing with Postman
1. Import collections from `postman_files/` folder
2. Set up environment variables in Postman:
   - `baseUrl`: `http://localhost:8080/api/v1`
   - `accessToken`: Will be set automatically after login
3. Start with `0auth.postman_collection.json` to test authentication

## 📝 License

This project is open source and available under the [MIT License](LICENSE).

## 🤝 Contributing

Contributions are welcome! Feel free to submit issues and pull requests.

## 📧 Support

For questions and support, please check the documentation in the `docs/` folder or create an issue.

## 🔄 Recent Updates

- ✅ Complete refresh token system with rotation
- ✅ Comprehensive frontend developer guide (700+ lines)
- ✅ Sensitive endpoint access logging
- ✅ User activity logging for all user types
- ✅ Token management for admins
- ✅ Refresh token administration
- ✅ Enhanced security with token rotation and reuse detection
### Adding New Endpoints
1. Create DTO in `dto/` for request/response
2. Create service in `services/` for business logic
3. Create controller in `controllers/` for REST endpoints
4. Update `SecurityConfig.java` if authentication required
5. Add logging if sensitive endpoint

### Email Templates
Customize HTML templates in `src/main/resources/templates/`:
- `verification-email.html` - Email verification
- `password-reset-email.html` - Password reset link
- `password-reset-success-email.html` - Password reset confirmation
- `verify.html` - Email verification success page
- `reset-password.html` - Password reset form page

### Admin Levels
Modify admin permission levels in:
- `models/Admin.java` - Add new levels
- `security/AdminLevelAuthorizationService.java` - Update authorization logic
- Controllers - Update `@PreAuthorize` annotations` | Delete/deactivate admin | Yes (Admin L0/L1) |
| POST | `/api/v1/admin/admins/{id}/activate` | Activate admin | Yes (Admin L0/L1) |
| POST | `/api/v1/admin/admins/{id}/deactivate` | Deactivate admin | Yes (Admin L0/L1) |
| POST | `/api/v1/admin/admins/{id}/reset-password` | Reset admin password | Yes (Admin L0/L1) |
| POST | `/api/v1/admin/admins/{id}/unlock` | Unlock admin account | Yes (Admin L0/L1) |

#### Image Upload (All Admin Levels)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/admin/image/profile` | Upload profile image | Yes (Admin) |
| PUT | `/api/v1/admin/image/profile` | Update profile image | Yes (Admin) |
| DELETE | `/api/v1/admin/image/profile` | Delete profile image | Yes (Admin) |
| POST | `/api/v1/admin/image/book-cover/{bookId}` | Upload book cover | Yes (Admin) |
| PUT | `/api/v1/admin/image/book-cover/{bookId}` | Update book cover | Yes (Admin) |
| DELETE | `/api/v1/admin/image/book-cover` | Delete book cover | Yes (Admin) |

#### Admin Logging (Level 0 Only)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/v1/admin/activity-logs` | Get admin activity logs | Yes (Admin L0) |
| GET | `/api/v1/admin/activity-logs/{id}` | Get activity log by ID | Yes (Admin L0) |
| GET | `/api/v1/admin/auth-error-logs` | Get auth error logs | Yes (Admin L0) |
| GET | `/api/v1/admin/auth-error-logs/{id}` | Get auth error log by ID | Yes (Admin L0) |
| GET | `/api/v1/admin/auth-error-logs/statistics` | Get auth error statistics | Yes (Admin L0) |
| GET | `/api/v1/admin/sensitive-access-logs` | Get sensitive access logs | Yes (Admin L0) |
| GET | `/api/v1/admin/sensitive-access-logs/{id}` | Get sensitive log by ID | Yes (Admin L0) |
| GET | `/api/v1/admin/sensitive-access-logs/statistics` | Get sensitive access statistics | Yes (Admin L0) |
| GET | `/api/v1/admin/user-activity-logs` | Get user activity logs | Yes (Admin L0) |
| GET | `/api/v1/admin/user-activity-logs/{id}` | Get user activity log by ID | Yes (Admin L0) |

#### Token Management (Level 0 Only)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/v1/admin/tokens/password-reset` | Get all password reset tokens | Yes (Admin L0) |
| GET | `/api/v1/admin/tokens/password-reset/{id}` | Get password reset token by ID | Yes (Admin L0) |
| DELETE | `/api/v1/admin/tokens/password-reset/{id}` | Delete password reset token | Yes (Admin L0) |
| GET | `/api/v1/admin/tokens/verification` | Get all verification tokens | Yes (Admin L0) |
| GET | `/api/v1/admin/tokens/verification/{id}` | Get verification token by ID | Yes (Admin L0) |
| DELETE | `/api/v1/admin/tokens/verification/{id}` | Delete verification token | Yes (Admin L0) |

#### Refresh Token Management (Level 0 Only)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/v1/admin/refresh-tokens` | Get all refresh tokens (with filters) | Yes (Admin L0) |
| GET | `/api/v1/admin/refresh-tokens/{id}` | Get refresh token by ID | Yes (Admin L0) |
| GET | `/api/v1/admin/refresh-tokens/user/{userId}` | Get active tokens for user | Yes (Admin L0) |
| GET | `/api/v1/admin/refresh-tokens/stats` | Get token statistics | Yes (Admin L0) |
| PUT | `/api/v1/admin/refresh-tokens/{id}/revoke` | Revoke refresh token | Yes (Admin L0) |
| PUT | `/api/v1/admin/refresh-tokens/revoke-all` | Revoke all user tokens | Yes (Admin L0) |
| DELETE | `/api/v1/admin/refresh-tokens/{id}` | Delete refresh token | Yes (Admin L0) |
| POST | `/api/v1/admin/refresh-tokens/cleanup` | Trigger manual cleanup | Yes (Admin L0) |

#### Database Backup (Level 0 Only)
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/admin/database-backup/create` | Manually trigger backup | Yes (Admin L0) |
| GET | `/api/v1/admin/database-backup/status` | Get backup status | Yes (Admin L0) |

### 📮 Postman Collections
Complete API documentation with examples in `postman_files/`:
- `0auth.postman_collection.json` - Authentication endpoints
- `11image_upload.postman_collection.json` - Image upload endpoints
- `13profile_management.postman_collection.json` - Profile management
- `19admin_admin_management.postman_collection.json` - Admin CRUD
- `22admin_activity_logs.postman_collection.json` - Activity logs
- `23auth_error_logs.postman_collection.json` - Auth error logs
- `24sensitive_access_logs.postman_collection.json` - Sensitive access logs
- `25user_activity_logs.postman_collection.json` - User activity logs
- `26.1_refresh_token_user.postman_collection.json` - Refresh token (user)
- `26.2_refresh_token_admin.postman_collection.json` - Refresh token (admin)n |
| GET | `/api/v1/admin/management/admins/{id}` | Get admin by ID |
| PUT | `/api/v1/admin/management/admins/{id}` | Update admin |
| DELETE | `/api/v1/admin/management/admins/{id}` | Delete admin |

### Admin Activity Logs
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/admin/activity-logs` | Get activity logs |
| GET | `/api/v1/admin/activity-logs/{id}` | Get log by ID |

## 🔐 Security Features

### Password Hashing
Uses Argon2id (winner of the Password Hashing Competition) with:
- Configurable memory cost (default: 64MB)
- Configurable time cost (default: 3 iterations)
- Configurable parallelism (default: 4 threads)
- Pepper (application-level secret)
- Per-user salt

### JWT Tokens
- Access tokens with configurable expiration
- Refresh tokens for seamless re-authentication
- Claims include: userId, userType, adminLevel

### Rate Limiting
- Login attempts: 5 per 15 minutes per IP
- API calls: 100 per minute per user
- Email verification: 3 per hour per email

## 🎨 Customization

### Adding New User Types
1. Create entity in `models/`
2. Create repository in `repos/`
3. Update `AuthService.java` registration logic
4. Update `SecurityConfig.java` authorization rules

### Adding New Endpoints
1. Create DTO in `dto/`
2. Create service in `services/`
3. Create controller in `controllers/`
4. Update `SecurityConfig.java` if needed

### Email Templates
Customize HTML templates in `src/main/resources/templates/`:
- `verification-email.html`
- `password-reset-email.html`
- `password-reset-success-email.html`

## 📝 License

This project is open source and available under the [MIT License](LICENSE).

## 🤝 Contributing

Contributions are welcome! Feel free to submit issues and pull requests.

---

**Built with ❤️ by g4stly**
