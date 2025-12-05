# Template Java Spring Boot REST API

A production-ready Spring Boot REST API template to kickstart your projects. Built with security, scalability, and developer experience in mind.

## 🚀 Features

### Authentication & Security
- **JWT Authentication** - Access & refresh token support with configurable expiration
- **Argon2id Password Hashing** - Industry-standard password hashing with pepper & salt
- **Two-Factor Authentication (2FA)** - TOTP-based 2FA compatible with Google Authenticator, Authy, etc.
- **Google reCAPTCHA** - Bot protection for admin login endpoints
- **Rate Limiting** - Configurable rate limits for login, API calls, and email verification
- **Security Headers** - HSTS, X-Frame-Options, Referrer-Policy, and more

### User Management
- **Multi-User Type Support** - Admin, Client, Coach user types (easily customizable)
- **Email Verification** - User email verification with customizable HTML templates
- **Password Reset** - Secure password reset flow with email notifications
- **Admin Management** - Full admin CRUD operations with activity logging
- **Admin Levels** - Hierarchical admin permission system

### Cloud & Storage
- **Cloudflare R2 Integration** - S3-compatible object storage for images/files
- **Image Upload Service** - Profile pictures, book covers, with validation

### Monitoring & Logging
- **Admin Activity Logging** - Track all admin actions (CRUD operations)
- **Authentication Error Logging** - Log failed login attempts with IP & User-Agent
- **Database Backup** - Automated MySQL backup with email notifications

### Developer Experience
- **Environment Variables** - Full `.env` support via spring-dotenv
- **Global Exception Handling** - Consistent error responses
- **DTO Pattern** - Clean separation of concerns
- **Async Processing** - Non-blocking activity logging

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
│   ├── TwoFactorAuthController.java
│   ├── AdminManagementController.java
│   ├── AdminProfileController.java
│   ├── AdminActivityLogController.java
│   ├── AdminAuthErrorController.java
│   ├── AdminImageController.java
│   ├── AdminTokenManagementController.java
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
│   ├── NotFoundException.java
│   └── ResourceNotFoundException.java
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
│   └── CustomAccessDeniedHandler.java
├── services/               # Business logic
│   ├── AuthService.java
│   ├── PasswordService.java
│   ├── TwoFactorAuthService.java
│   ├── EmailService.java
│   ├── RateLimitService.java
│   ├── ImageUploadService.java
│   ├── DatabaseBackupService.java
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
RATE_LIMIT_LOGIN_ATTEMPTS=5
RATE_LIMIT_LOGIN_WINDOW=900000
RATE_LIMIT_API_CALLS=100
RATE_LIMIT_API_WINDOW=60000

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

## 📚 API Endpoints

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register new user |
| POST | `/api/v1/auth/login` | User login |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| POST | `/api/v1/auth/forgot-password` | Request password reset |
| POST | `/api/v1/auth/reset-password` | Reset password |
| GET | `/api/v1/auth/verify` | Verify email |
| POST | `/api/v1/auth/resend-verification` | Resend verification email |

### Two-Factor Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/admin/2fa/setup` | Generate 2FA secret & QR code |
| POST | `/api/v1/admin/2fa/verify` | Verify & enable 2FA |
| POST | `/api/v1/admin/2fa/verify-login` | Complete 2FA login |
| POST | `/api/v1/admin/2fa/disable` | Disable 2FA |

### Admin Management (Admin only)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/admin/management/admins` | List all admins |
| POST | `/api/v1/admin/management/admins` | Create new admin |
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
