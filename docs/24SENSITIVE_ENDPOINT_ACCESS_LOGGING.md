# Sensitive Endpoint Access Logging System

## Overview

Security monitoring system that logs successful access attempts to sensitive/important endpoints and sends email alerts for high-priority access events. This system provides audit trails for critical operations and helps detect potential security threats or unusual access patterns.

## Features

### 1. Automatic Access Logging
- All successful access to sensitive endpoints is logged
- Asynchronous logging (separate thread) - doesn't block main request processing
- Detailed capture of user information, IP addresses, and request context
- Severity-based categorization (LOW, MEDIUM, HIGH, CRITICAL)

### 2. Email Alerts
- Automatic email alerts for HIGH and CRITICAL severity access
- Detailed HTML email with security alert information
- Sent asynchronously to not affect main request flow
- Configurable via application properties

### 3. Severity Levels
| Level | Description | Email Alert |
|-------|-------------|-------------|
| LOW | Informational - routine access | No |
| MEDIUM | Requires attention - sensitive data access | No |
| HIGH | Immediate review required - admin operations | Yes |
| CRITICAL | Security alert - database/backup operations | Yes |

### 4. Endpoint Categories

#### Admin Endpoints
| Category | Severity | Endpoints |
|----------|----------|-----------|
| DATABASE_BACKUP | CRITICAL | `/api/v1/admin/backup/**` |
| ADMIN_MANAGEMENT | HIGH | `/api/v1/admin/admins/**` |
| TOKEN_MANAGEMENT | HIGH | `/api/v1/admin/tokens/**` |
| 2FA_SETTINGS | MEDIUM | `/api/v1/admin/2fa/setup`, `/enable`, `/disable` |
| ACTIVITY_LOGS | MEDIUM | `/api/v1/admin/activity-logs/**` |
| ERROR_LOGS | MEDIUM | `/api/v1/admin/auth-error-logs/**` |
| IMAGE_MANAGEMENT | LOW | `/api/v1/admin/images/**` |

#### Suspicious File Access (Security Monitoring)
| Category | Severity | Pattern Examples |
|----------|----------|------------------|
| SUSPICIOUS_FILE_ACCESS | CRITICAL | `.env`, `.env.local`, `.env.production` |
| SUSPICIOUS_FILE_ACCESS | CRITICAL | `.git/`, `.git/config`, `.gitignore` |
| SUSPICIOUS_FILE_ACCESS | CRITICAL | `application.properties`, `application.yml` |
| SUSPICIOUS_FILE_ACCESS | CRITICAL | `.aws/credentials`, SSH keys (`id_rsa`, `.pem`) |
| SUSPICIOUS_FILE_ACCESS | CRITICAL | Database files (`.sql`, `.db`, `.sqlite`) |
| SUSPICIOUS_FILE_ACCESS | CRITICAL | Backup files (`.bak`, `.backup`) |
| SUSPICIOUS_FILE_ACCESS | CRITICAL | Secrets files (`secrets`, `password`, `api_key`) |
| SUSPICIOUS_FILE_ACCESS | CRITICAL | Keystore files (`.jks`, `.p12`, `.pfx`) |
| SUSPICIOUS_FILE_ACCESS | HIGH | Log files (`.log`, `/logs/`) |
| SUSPICIOUS_FILE_ACCESS | HIGH | Source code (`.java`, `.class`, `.jar`, `.war`) |
| SUSPICIOUS_FILE_ACCESS | HIGH | Archive files (`.zip`, `.tar`, `.gz`) |
| SUSPICIOUS_FILE_ACCESS | HIGH | Docker/CI files (`Dockerfile`, `.travis.yml`) |
| SUSPICIOUS_FILE_ACCESS | MEDIUM | Package files (`package.json`, `pom.xml`) |

#### Suspicious Path Probes (Attack Detection)
| Category | Severity | Pattern Examples |
|----------|----------|------------------|
| SUSPICIOUS_PATH_PROBE | CRITICAL | `/shell`, `/cmd`, `/exec` |
| SUSPICIOUS_PATH_PROBE | HIGH | `/phpmyadmin`, `/adminer`, `/wp-admin` |
| SUSPICIOUS_PATH_PROBE | HIGH | `/actuator/*` (except health), `/debug`, `/trace`, `/console` |
| SUSPICIOUS_PATH_PROBE | MEDIUM | `/admin/` (non-API paths) |

### 5. Admin Panel Integration
- View all access logs with filtering options
- Filter by user ID, user type, severity, category, IP address, date range
- View detailed access statistics
- Delete logs (Level 0 Super Admins only)

## Architecture

### Entity Layer
**`SensitiveEndpointAccessLog.java`**
- Stores sensitive endpoint access details in database
- Fields:
  - `severity`: Enum (LOW, MEDIUM, HIGH, CRITICAL)
  - `userId`: User ID who accessed the endpoint
  - `userType`: Type of user (admin, coach, client)
  - `username`: Username of the accessor
  - `ipAddress`: Client IP address
  - `userAgent`: Browser/client user agent
  - `endpoint`: Requested URL path
  - `httpMethod`: HTTP method (GET, POST, etc.)
  - `endpointCategory`: Category of the endpoint
  - `description`: Description of the access
  - `responseStatus`: HTTP response status code
  - `emailAlertSent`: Whether email alert was sent
  - `createdAt`: Timestamp

### Repository Layer
**`SensitiveEndpointAccessLogRepository.java`**
- JPA repository with advanced query methods:
  - `findBySeverity()`: Filter by severity level
  - `findByUserId()`: Filter by user ID
  - `findByUserType()`: Filter by user type
  - `findByIpAddress()`: Filter by IP address
  - `findByEndpointCategory()`: Filter by category
  - `getStatisticsBySeverity()`: Aggregate statistics
  - `getStatisticsByCategory()`: Category breakdown
  - `getDailyStatistics()`: Daily access trends

### Service Layer
**`SensitiveEndpointAccessLogService.java`**
- Async logging with `@Async` and `REQUIRES_NEW` propagation
- Automatic email alerts for HIGH/CRITICAL severity
- Fail-safe error handling (logging errors don't affect main flow)
- Formatted console output with box drawing
- Specialized methods:
  - `logDatabaseBackupAccess()`: CRITICAL severity
  - `logAdminManagementAccess()`: HIGH severity
  - `logTokenManagementAccess()`: HIGH severity
  - `log2FASettingsAccess()`: MEDIUM severity
  - `logActivityLogsAccess()`: MEDIUM severity
  - `logErrorLogsAccess()`: MEDIUM severity

**`AdminSensitiveAccessService.java`**
- Admin service for reading and managing access logs
- Pagination and filtering support
- Statistics generation
- Delete functionality (Level 0 only)

### Filter Layer
**`SensitiveEndpointAccessFilter.java`**
- Intercepts requests to sensitive endpoints
- Runs after authentication
- Logs only successful requests (2xx status codes)
- Extracts user information from JWT token
- Asynchronous logging call

### Controller Layer
**`AdminSensitiveAccessController.java`**
- REST API endpoints for admin panel
- Level 0 Super Admin access only

## API Endpoints

### Get All Access Logs
```
GET /api/v1/admin/sensitive-access-logs
```

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| page | int | 0 | Page number |
| size | int | 20 | Page size |
| sortBy | string | createdAt | Sort field |
| sortDirection | string | desc | Sort direction (asc/desc) |
| userId | Long | null | Filter by user ID |
| userType | string | null | Filter by user type (admin, coach, client) |
| severity | string | null | Filter by severity (LOW, MEDIUM, HIGH, CRITICAL) |
| category | string | null | Filter by endpoint category |
| ipAddress | string | null | Filter by IP address |
| startDate | ISO DateTime | null | Filter logs after this date |

**Access:** Level 0 Super Admins only

### Get Access Log by ID
```
GET /api/v1/admin/sensitive-access-logs/{id}
```
**Access:** Level 0 Super Admins only

### Get Access Logs by User ID
```
GET /api/v1/admin/sensitive-access-logs/user/{userId}
```
**Access:** Level 0 Super Admins only

### Get Access Logs by IP Address
```
GET /api/v1/admin/sensitive-access-logs/ip/{ipAddress}
```
**Access:** Level 0 Super Admins only

### Get Access Statistics
```
GET /api/v1/admin/sensitive-access-logs/statistics
```
**Access:** Level 0 Super Admins only

**Response:**
```json
{
  "success": true,
  "data": {
    "totalAccessLogs": 1250,
    "criticalCount": 50,
    "highCount": 200,
    "mediumCount": 500,
    "lowCount": 500,
    "emailAlertsSent": 250,
    "accessBySeverity": {
      "CRITICAL": 50,
      "HIGH": 200,
      "MEDIUM": 500,
      "LOW": 500
    },
    "accessByCategory": {
      "DATABASE_BACKUP": 50,
      "ADMIN_MANAGEMENT": 150,
      "TOKEN_MANAGEMENT": 100,
      "2FA_SETTINGS": 200,
      "ACTIVITY_LOGS": 300
    },
    "dailyStatistics": {
      "2024-12-01": 45,
      "2024-12-02": 52
    }
  }
}
```

### Delete Access Log
```
DELETE /api/v1/admin/sensitive-access-logs/{id}
```
**Access:** Level 0 Super Admins only

## Configuration

**application.properties**
```properties
# Enable/disable sensitive endpoint access logging
app.security.log-sensitive-access=true

# Enable/disable email alerts for HIGH/CRITICAL access
app.security.sensitive-access-email-alert=true

# Admin email for security alerts
app.admin.email=admin@yourdomain.com
```

## Database Schema

```sql
CREATE TABLE sensitive_endpoint_access_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    severity VARCHAR(20) NOT NULL,
    user_id BIGINT,
    user_type VARCHAR(20),
    username VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent TEXT,
    endpoint VARCHAR(500) NOT NULL,
    http_method VARCHAR(10),
    endpoint_category VARCHAR(100),
    description TEXT,
    response_status INT,
    email_alert_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sensitive_access_user_id (user_id),
    INDEX idx_sensitive_access_user_type (user_type),
    INDEX idx_sensitive_access_endpoint (endpoint),
    INDEX idx_sensitive_access_created_at (created_at),
    INDEX idx_sensitive_access_ip_address (ip_address),
    INDEX idx_sensitive_access_severity (severity)
);
```

## Console Output Example

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ 🔐 Sensitive Endpoint Access Logged                                            ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Severity: CRITICAL - Critical - Security Alert                                 ║
║ Category: DATABASE_BACKUP                                                      ║
║ User ID: 1                                                                     ║
║ Username: superadmin                                                           ║
║ IP Address: 192.168.1.100                                                      ║
║ Endpoint: /api/v1/admin/backup/create                                          ║
║ Description: Database backup operation accessed                                ║
║ Email Alert: ✓ Sent                                                            ║
╚════════════════════════════════════════════════════════════════════════════════╝
```

## Email Alert Example

For HIGH and CRITICAL severity access, an HTML email is sent to the admin with:
- Severity level and description
- Timestamp of access
- Endpoint category
- Full endpoint path and HTTP method
- User ID and username
- User type
- IP address
- Response status
- Description of the action

## Security Monitoring Use Cases

### 1. Detect Unusual Admin Activity
```java
// Check if an admin has accessed many sensitive endpoints recently
Long count = accessLogRepository.countByUserIdSince(
    adminId,
    LocalDateTime.now().minusHours(1)
);
if (count > 50) {
    // Flag for review
}
```

### 2. Monitor Database Backup Access
```java
// Get all database backup access logs
SensitiveAccessLogListResponse logs = adminSensitiveAccessService.getAllLogs(
    adminId, 0, 100, "createdAt", "desc", 
    null, null, "CRITICAL", "DATABASE_BACKUP", null, null, request
);
```

### 3. Track Access from Specific IP
```java
// Monitor access from suspicious IP
SensitiveAccessLogListResponse logs = adminSensitiveAccessService.getLogsByIpAddress(
    adminId, suspiciousIp, 0, 50, request
);
```

### 4. Monitor Suspicious File Access Attempts
```java
// Get all suspicious file access attempts (security probing)
SensitiveAccessLogListResponse logs = adminSensitiveAccessService.getAllLogs(
    adminId, 0, 100, "createdAt", "desc", 
    null, null, "CRITICAL", "SUSPICIOUS_FILE_ACCESS", null, null, request
);
```

### 5. Detect Security Reconnaissance
```java
// Get all suspicious path probes (common attack patterns)
SensitiveAccessLogListResponse logs = adminSensitiveAccessService.getAllLogs(
    adminId, 0, 100, "createdAt", "desc", 
    null, null, null, "SUSPICIOUS_PATH_PROBE", null, null, request
);
```

## Suspicious File/Path Monitoring

The system automatically monitors and logs successful access to commonly targeted security-sensitive files and paths:

### Environment & Configuration Files
- `.env`, `.env.local`, `.env.production`, `.env.*`
- `application.properties`, `application.yml`, `application-*.properties`
- `config.*`, `bootstrap.properties`

### Source Control
- `.git/`, `.git/config`, `.git/HEAD`
- `.gitignore`

### Credentials & Keys
- `.aws/credentials`, AWS credential files
- SSH keys: `id_rsa`, `id_ed25519`, `.pem`, `.key`
- Keystores: `.jks`, `.p12`, `.pfx`
- Files containing: `secrets`, `password`, `api_key`, `private_key`

### Database Files
- `.sql`, `.db`, `.sqlite`
- `dump*.sql`, `backup*.sql`

### Backup Files
- `.bak`, `.backup`, `.old`, `.orig`, `.copy`, `~`

### Server Configuration
- `.htaccess`, `.htpasswd`
- `wp-config.php`, `php.ini`, `web.config`

### Build & Deployment
- `Dockerfile`, `docker-compose.yml`
- `.travis.yml`, `.gitlab-ci.yml`, `Jenkinsfile`
- `.github/`

### Common Attack Paths
- `/phpmyadmin`, `/adminer`
- `/wp-admin`, `/wp-login`
- `/actuator/*` (except `/actuator/health`)
- `/debug`, `/trace`, `/console`
- `/shell`, `/cmd`, `/exec`

**Note:** Only successful access (2xx HTTP status) triggers logging. This ensures that if someone actually manages to access these files, it's immediately flagged as a critical security event.

## Asynchronous Processing

The logging system uses Spring's `@Async` annotation to ensure logging doesn't block the main request:

1. **Filter intercepts request** → Continues with filter chain
2. **After successful response** → Async logging method called
3. **Main thread returns** → Response sent to client
4. **Background thread** → Logs to database, sends email if needed

This ensures:
- No latency added to API responses
- Email sending doesn't block requests
- Logging failures don't affect main operations

## Files Created

### New Files
1. `models/SensitiveEndpointAccessLog.java` - Entity model
2. `repos/SensitiveEndpointAccessLogRepository.java` - Repository with queries
3. `services/SensitiveEndpointAccessLogService.java` - Async logging service
4. `services/AdminSensitiveAccessService.java` - Admin management service
5. `controllers/AdminSensitiveAccessController.java` - REST API endpoints
6. `security/SensitiveEndpointAccessFilter.java` - Request filter
7. `dto/admin/SensitiveAccessLogResponse.java` - Response DTO
8. `dto/admin/SensitiveAccessLogListResponse.java` - List response DTO
9. `dto/admin/SensitiveAccessStatisticsResponse.java` - Statistics DTO
10. `postman_files/24sensitive_access_logs.postman_collection.json` - Postman collection

### Modified Files
1. `application.properties` - Added configuration properties

## Example API Usage

### Get All Access Logs with Filtering
```bash
curl -X GET "http://localhost:8080/api/v1/admin/sensitive-access-logs?severity=CRITICAL&page=0&size=20" \
  -H "Authorization: Bearer YOUR_LEVEL0_ADMIN_TOKEN"
```

### Get Access Logs by Category
```bash
curl -X GET "http://localhost:8080/api/v1/admin/sensitive-access-logs?category=DATABASE_BACKUP&page=0&size=20" \
  -H "Authorization: Bearer YOUR_LEVEL0_ADMIN_TOKEN"
```

### Get Access Statistics
```bash
curl -X GET "http://localhost:8080/api/v1/admin/sensitive-access-logs/statistics" \
  -H "Authorization: Bearer YOUR_LEVEL0_ADMIN_TOKEN"
```
