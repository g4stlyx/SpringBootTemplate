# User Activity Logging System

## Overview

This document describes the user activity logging system for the template application. This feature provides comprehensive audit trails for all significant user (Client/Coach) actions, enabling security monitoring, compliance, and debugging.

## Features

### 1. Asynchronous Logging

All user activity logging is performed **asynchronously** using Spring's `@Async` annotation:
- **Non-blocking**: Logging operations do not slow down the main request processing
- **Separate Transaction**: Uses `Propagation.REQUIRES_NEW` to ensure logging doesn't interfere with main operations
- **Fault-tolerant**: Logging failures do not affect user operations

### 2. Comprehensive Action Tracking

The system logs the following user actions:

| Action | Description |
|--------|-------------|
| LOGIN | User login attempts (success/failure) |
| LOGOUT | User logout events |
| REGISTER | New user registration |
| PASSWORD_RESET_REQUEST | User requests password reset |
| PASSWORD_RESET_COMPLETE | User completes password reset |
| PASSWORD_CHANGE | User changes their password |
| EMAIL_VERIFICATION | User verifies their email |
| VERIFICATION_EMAIL_RESENT | User requests new verification email |
| PROFILE_UPDATE | User updates their profile |
| PROFILE_PICTURE_UPLOAD | User uploads profile picture |
| PROFILE_PICTURE_DELETE | User deletes profile picture |
| ACCOUNT_DEACTIVATED | User account is deactivated |
| ACCOUNT_REACTIVATED | User account is reactivated |
| SESSION_REFRESH | User refreshes their session token |
| READ | User views a resource |
| CREATE | User creates a resource |
| UPDATE | User updates a resource |
| DELETE | User deletes a resource |

### 3. Admin Management Interface

Super Admins (Level 0) can view and filter all user activity logs through dedicated API endpoints.

## Database Schema

### user_activity_log Table

```sql
CREATE TABLE user_activity_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_type VARCHAR(20) NOT NULL,  -- 'client' or 'coach'
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    details JSON,
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_activity_user (user_id, user_type),
    INDEX idx_user_activity_action (action),
    INDEX idx_user_activity_created_at (created_at)
);
```

## API Endpoints

All endpoints are restricted to **Level 0 (Super Admin) only**.

### Get All User Activity Logs

```
GET /api/v1/admin/user-activity-logs
```

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| page | int | 0 | Page number |
| size | int | 20 | Page size |
| sortBy | string | createdAt | Sort field |
| sortDirection | string | desc | Sort direction (asc/desc) |
| userId | Long | null | Filter by user ID |
| userType | string | null | Filter by user type (client/coach) |
| action | string | null | Filter by action type |
| resourceType | string | null | Filter by resource type |
| success | Boolean | null | Filter by success status |
| startDate | ISO DateTime | null | Filter logs after this date |
| endDate | ISO DateTime | null | Filter logs before this date |
| ipAddress | string | null | Filter by IP address |

**Response:**
```json
{
  "success": true,
  "data": {
    "logs": [
      {
        "id": 1,
        "userId": 5,
        "userType": "client",
        "username": "john_doe",
        "email": "john@example.com",
        "action": "LOGIN",
        "resourceType": "Authentication",
        "resourceId": null,
        "details": "{\"event\":\"login_attempt\"}",
        "ipAddress": "192.168.1.100",
        "userAgent": "Mozilla/5.0...",
        "success": true,
        "failureReason": null,
        "createdAt": "2024-01-15T14:30:00"
      }
    ],
    "currentPage": 0,
    "totalPages": 10,
    "totalElements": 200,
    "pageSize": 20,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### Get User Activity Log by ID

```
GET /api/v1/admin/user-activity-logs/{logId}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 5,
    "userType": "client",
    "username": "john_doe",
    "email": "john@example.com",
    "action": "PASSWORD_RESET_COMPLETE",
    "resourceType": "Security",
    "resourceId": null,
    "details": "{\"event\":\"password_reset_completed\"}",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0...",
    "success": true,
    "failureReason": null,
    "createdAt": "2024-01-15T14:30:00"
  }
}
```

### Get Activity Logs for Specific User

```
GET /api/v1/admin/user-activity-logs/user/{userId}
```

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| userType | string | client | User type (client/coach) |
| page | int | 0 | Page number |
| size | int | 20 | Page size |
| sortBy | string | createdAt | Sort field |
| sortDirection | string | desc | Sort direction (asc/desc) |

### Get Activity Statistics

```
GET /api/v1/admin/user-activity-logs/statistics
```

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| since | ISO DateTime | 30 days ago | Start date for statistics |

**Response:**
```json
{
  "success": true,
  "data": {
    "totalLogins": 1500,
    "totalRegistrations": 250,
    "totalPasswordResets": 45,
    "totalProfileUpdates": 320,
    "successfulActions": 2100,
    "failedActions": 15,
    "periodStart": "2024-01-01T00:00:00",
    "periodEnd": "2024-01-31T23:59:59"
  }
}
```

## Implementation Details

### Key Files

1. **Model**: `src/main/java/com/g4stly/templateApp/models/UserActivityLog.java`
   - JPA entity for user activity logs
   - Indexes for common query patterns

2. **Repository**: `src/main/java/com/g4stly/templateApp/repos/UserActivityLogRepository.java`
   - JPA repository with custom query methods
   - Supports JPA Specifications for dynamic filtering

3. **Logger Service**: `src/main/java/com/g4stly/templateApp/services/UserActivityLogger.java`
   - Async logging service
   - Convenience methods for common actions
   - IP address extraction from proxied requests

4. **Management Service**: `src/main/java/com/g4stly/templateApp/services/UserActivityLogService.java`
   - Business logic for reading and filtering logs
   - Statistics generation
   - DTO conversion

5. **Controller**: `src/main/java/com/g4stly/templateApp/controllers/AdminUserActivityLogController.java`
   - REST endpoints for admin access
   - Level 0 authorization check

6. **DTOs**: 
   - `src/main/java/com/g4stly/templateApp/dto/admin/UserActivityLogDTO.java`
   - `src/main/java/com/g4stly/templateApp/dto/admin/UserActivityLogListResponse.java`

### Async Configuration

The async logging relies on the existing `AsyncConfig`:

```java
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    // Enables @Async methods including UserActivityLogger
}
```

### Usage Example

To log user activity in a service:

```java
@Autowired
private UserActivityLogger userActivityLogger;

// Log successful login
userActivityLogger.logLoginSuccess(userId, "client", httpRequest);

// Log failed login
userActivityLogger.logLoginFailure(userId, "client", "Invalid password", httpRequest);

// Log profile update with details
Map<String, Object> changedFields = new HashMap<>();
changedFields.put("firstName", "New Name");
userActivityLogger.logProfileUpdate(userId, "coach", changedFields, httpRequest);

// Log custom action
Map<String, Object> details = new HashMap<>();
details.put("customField", "value");
userActivityLogger.logActivity(userId, "client", "CUSTOM_ACTION", 
    "ResourceType", "resourceId", details, true, null, httpRequest);
```

## Security Considerations

### Access Control

| Endpoint Type | Required Role | Required Level |
|---------------|---------------|----------------|
| View User Activity Logs | ADMIN | Level 0 (Super Admin) |
| View Activity Statistics | ADMIN | Level 0 (Super Admin) |

### Privacy

- User activity logs contain potentially sensitive information (IP addresses, user agents, action history)
- Access is restricted to Level 0 Super Admins only
- Logs should be periodically cleaned up based on data retention policies

### Authorization Flow

1. Request includes JWT token in Authorization header
2. `@PreAuthorize("hasRole('ADMIN')")` validates admin role
3. Controller extracts admin ID from JWT
4. Controller checks `admin.getLevel() == 0`
5. Returns 403 Forbidden if level check fails

## Automatic Logging Integration

The following actions are **automatically logged** in the AuthService:

| Action | Trigger |
|--------|---------|
| REGISTER | Successful client/coach registration |
| LOGIN | Login success or failure |
| EMAIL_VERIFICATION | Email verification completion |
| PASSWORD_RESET_REQUEST | Password reset initiation |
| PASSWORD_RESET_COMPLETE | Password reset completion |

### Extending Logging

To add activity logging to other parts of the application:

1. Inject `UserActivityLogger` into your service
2. Call appropriate logging method after significant actions
3. For custom actions, use the generic `logActivity()` method

## Data Retention

Consider implementing a scheduled cleanup task for old activity logs:

```java
@Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
public void cleanupOldLogs() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
    userActivityLogService.deleteOldActivityLogs(cutoff);
}
```

## Example Usage

### Filter Logs by Action Type

```bash
curl -X GET "http://localhost:8080/api/v1/admin/user-activity-logs?action=LOGIN&page=0&size=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Filter Logs by User Type

```bash
curl -X GET "http://localhost:8080/api/v1/admin/user-activity-logs?userType=client&page=0&size=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Filter Failed Actions

```bash
curl -X GET "http://localhost:8080/api/v1/admin/user-activity-logs?success=false&page=0&size=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Filter by Date Range

```bash
curl -X GET "http://localhost:8080/api/v1/admin/user-activity-logs?startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Get User's Activity History

```bash
curl -X GET "http://localhost:8080/api/v1/admin/user-activity-logs/user/5?userType=client" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```
