# Authentication Error Logging System

## Overview

Comprehensive authentication and authorization error logging system that tracks all HTTP errors (401, 403, 404, 400, 500) with detailed user information, IP addresses, and request context. This system enables admins to monitor security events, detect potential attacks, and troubleshoot user access issues.

## Features

### 1. Automatic Error Logging
- All authentication errors (401 Unauthorized)
- All authorization errors (403 Forbidden)
- All resource not found errors (404 Not Found)
- All bad request errors (400 Bad Request)
- All server errors (500 Internal Server Error)
- Invalid token errors
- Access denied events

### 2. 404 for Non-Existent Paths
- The app now returns 404 instead of 500 for non-existent API paths
- All 404 errors are logged with user context if available

### 3. Admin Panel Integration
- View all error logs with filtering options
- Filter by user ID, user type, error type, IP address, date range
- View detailed error statistics
- Delete logs (Level 0 Super Admins only)

## Architecture

### Entity Layer
**`AuthenticationErrorLog.java`**
- Stores authentication error details in database
- Fields:
  - `errorType`: Enum (UNAUTHORIZED_401, FORBIDDEN_403, NOT_FOUND_404, BAD_REQUEST_400, INTERNAL_SERVER_ERROR_500, INVALID_TOKEN, ACCESS_DENIED)
  - `userId`: User ID (if identified)
  - `userType`: Type of user (admin, coach, client)
  - `username`: Username (if available)
  - `ipAddress`: Client IP address
  - `userAgent`: Browser/client user agent
  - `endpoint`: Requested URL path
  - `httpMethod`: HTTP method (GET, POST, etc.)
  - `errorMessage`: Error details
  - `attemptedAction`: Description of what was attempted
  - `createdAt`: Timestamp

### Repository Layer
**`AuthenticationErrorLogRepository.java`**
- JPA repository with advanced query methods:
  - `findByErrorType()`: Filter by error type
  - `findByUserId()`: Filter by user ID
  - `findByUserType()`: Filter by user type
  - `findByIpAddress()`: Filter by IP address
  - `findByDateRange()`: Filter by date range
  - `countByIpAddressSince()`: Count errors from IP (for rate limiting)
  - `getStatisticsByErrorType()`: Aggregate statistics
  - `getDailyStatistics()`: Daily error trends

### Service Layer
**`AuthErrorLogService.java`**
- Async logging with `@Async` and `REQUIRES_NEW` propagation
- Fail-safe error handling (logging errors don't affect main flow)
- Formatted console output with box drawing
- Specialized methods:
  - `log401()`: Unauthorized errors
  - `log403()`: Forbidden errors
  - `log404()`: Not found errors
  - `log400()`: Bad request errors
  - `log500()`: Server errors
  - `logInvalidToken()`: Token errors
  - `logAccessDenied()`: Access denied events

**`AdminAuthErrorService.java`**
- Admin service for reading and managing error logs
- Pagination and filtering support
- Statistics generation
- Delete functionality (Level 0 only)

### Controller Layer
**`AdminAuthErrorController.java`**
- REST API endpoints for admin panel

### Security Layer
**`CustomAccessDeniedHandler.java`**
- Custom handler for 403 errors
- Extracts user info from JWT token
- Logs detailed access denied events

**`JwtAuthEntryPoint.java`** (Updated)
- Now logs 401 errors automatically
- Captures IP address and user agent

**`GlobalExceptionHandler.java`** (Updated)
- Logs all exception types
- Handles NoHandlerFoundException for 404 on non-existent paths
- Extracts user info from security context and JWT token

## API Endpoints

### Get All Error Logs
```
GET /api/v1/admin/auth-error-logs
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
| errorType | string | null | Filter by error type |
| ipAddress | string | null | Filter by IP address |
| startDate | ISO DateTime | null | Filter logs after this date |

**Access:** All Admins

### Get Error Log by ID
```
GET /api/v1/admin/auth-error-logs/{id}
```
**Access:** All Admins

### Get Error Logs by User ID
```
GET /api/v1/admin/auth-error-logs/user/{userId}
```
**Access:** All Admins

### Get Error Logs by IP Address
```
GET /api/v1/admin/auth-error-logs/ip/{ipAddress}
```
**Access:** All Admins

### Get Error Statistics
```
GET /api/v1/admin/auth-error-logs/statistics
```
**Access:** All Admins

**Response:**
```json
{
  "success": true,
  "data": {
    "totalErrors": 1250,
    "unauthorized401Count": 450,
    "forbidden403Count": 200,
    "notFound404Count": 300,
    "badRequest400Count": 150,
    "internalServerError500Count": 50,
    "invalidTokenCount": 75,
    "accessDeniedCount": 25,
    "errorsByType": {
      "UNAUTHORIZED_401": 450,
      "FORBIDDEN_403": 200,
      "NOT_FOUND_404": 300
    },
    "dailyStatistics": {
      "2024-12-01": 45,
      "2024-12-02": 52
    }
  }
}
```

### Delete Error Log
```
DELETE /api/v1/admin/auth-error-logs/{id}
```
**Access:** Level 0 Super Admins only

## Error Types

| Error Type | HTTP Code | Description |
|-----------|-----------|-------------|
| UNAUTHORIZED_401 | 401 | Authentication required |
| FORBIDDEN_403 | 403 | Insufficient permissions |
| NOT_FOUND_404 | 404 | Resource or path not found |
| BAD_REQUEST_400 | 400 | Invalid request data |
| INTERNAL_SERVER_ERROR_500 | 500 | Server error |
| INVALID_TOKEN | 401 | Invalid or expired JWT token |
| ACCESS_DENIED | 403 | Generic access denial |

## Console Output Example

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ Authentication Error Detected                                                  ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Error Type: FORBIDDEN_403 - Forbidden - Insufficient Permissions              ║
║ User ID: 42                                                                    ║
║ Username: john.doe                                                             ║
║ IP Address: 192.168.1.100                                                      ║
║ Endpoint: /api/v1/admin/users                                                  ║
║ Message: Access denied to admin resource                                       ║
╚════════════════════════════════════════════════════════════════════════════════╝
```

## Configuration

**application.properties**
```properties
# Enable/disable authentication error logging
app.security.log-auth-errors=true

# Enable 404 for non-existent paths (instead of default 500)
spring.mvc.throw-exception-if-no-handler-found=true
spring.web.resources.add-mappings=false
```

## Database Schema

```sql
CREATE TABLE authentication_error_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    error_type VARCHAR(30) NOT NULL,
    user_id BIGINT,
    user_type VARCHAR(20),
    username VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent TEXT,
    endpoint VARCHAR(500) NOT NULL,
    http_method VARCHAR(10),
    error_message TEXT,
    attempted_action VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_auth_error_type (error_type),
    INDEX idx_auth_error_user_id (user_id),
    INDEX idx_auth_error_user_type (user_type),
    INDEX idx_auth_error_created_at (created_at),
    INDEX idx_auth_error_ip_address (ip_address)
);
```

## Security Monitoring Use Cases

### 1. Detect Brute Force Attacks
```java
// Check if IP has too many 401 errors in 15 minutes
Long count = authErrorLogRepository.countByIpAddressSince(
    ipAddress,
    LocalDateTime.now().minusMinutes(15)
);
if (count > 10) {
    // Block IP or trigger alert
}
```

### 2. Monitor Suspicious User Activity
```java
// Get recent errors for a specific user
AuthErrorLogListResponse logs = adminAuthErrorService.getLogsByUserId(
    adminId, suspiciousUserId, 0, 50, request
);
```

### 3. Analyze Error Trends
```java
// Get statistics for monitoring dashboard
AuthErrorStatisticsResponse stats = adminAuthErrorService.getStatistics(adminId, request);
```

## Files Created/Modified

### New Files
1. `models/AuthenticationErrorLog.java` - Entity model
2. `repos/AuthenticationErrorLogRepository.java` - Repository with queries
3. `services/AuthErrorLogService.java` - Async logging service
4. `services/AdminAuthErrorService.java` - Admin management service
5. `controllers/AdminAuthErrorController.java` - REST API endpoints
6. `dto/admin/AuthErrorLogResponse.java` - Response DTO
7. `dto/admin/AuthErrorLogListResponse.java` - List response DTO
8. `dto/admin/AuthErrorStatisticsResponse.java` - Statistics DTO
9. `security/CustomAccessDeniedHandler.java` - 403 handler
10. `postman_files/22auth_error_logs.postman_collection.json` - Postman collection

### Modified Files
1. `security/JwtAuthEntryPoint.java` - Added 401 error logging
2. `exception/GlobalExceptionHandler.java` - Added error logging for all exceptions
3. `config/SecurityConfig.java` - Added CustomAccessDeniedHandler
4. `application.properties` - Added configuration properties

## Example API Usage

### Get All Error Logs with Filtering
```bash
curl -X GET "http://localhost:8080/api/v1/admin/auth-error-logs?errorType=UNAUTHORIZED_401&page=0&size=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Get Error Logs by IP Address
```bash
curl -X GET "http://localhost:8080/api/v1/admin/auth-error-logs/ip/192.168.1.100?page=0&size=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Get Error Statistics
```bash
curl -X GET "http://localhost:8080/api/v1/admin/auth-error-logs/statistics" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```
