# Admin Activity Logs & Token Management

## Overview

This document describes the admin activity logs viewing system and automated token cleanup for the Further API. These features allow Super Admins (Level 0) to monitor admin actions and automatically clean up expired tokens.

## Features

### 1. Admin Activity Logs

Admin activity logs track all significant actions performed by administrators in the system. This provides:
- **Audit Trail**: Complete history of admin actions
- **Security Monitoring**: Track sensitive operations
- **Compliance**: Meet regulatory requirements for action logging
- **Debugging**: Trace issues to specific admin actions

### 2. Automated Token Cleanup

The system automatically cleans up expired tokens:
- **Password Reset Tokens**: Expire after 15 minutes
- **Verification Tokens**: Expire after 24 hours
- **Scheduled Cleanup**: Runs daily at 2:00 AM

## API Endpoints

### Admin Activity Logs

All activity log endpoints are restricted to **Level 0 (Super Admin) only**.

#### Get All Activity Logs

```
GET /api/v1/admin/activity-logs
```

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| page | int | 0 | Page number |
| size | int | 20 | Page size |
| sortBy | string | createdAt | Sort field |
| sortDirection | string | desc | Sort direction (asc/desc) |
| adminId | Long | null | Filter by admin ID |
| action | string | null | Filter by action type |
| resourceType | string | null | Filter by resource type |
| startDate | ISO DateTime | null | Filter logs after this date |

**Response:**
```json
{
  "success": true,
  "data": {
    "logs": [
      {
        "id": 1,
        "adminId": 1,
        "adminUsername": "superadmin",
        "adminEmail": "admin@example.com",
        "action": "READ",
        "resourceType": "Coach",
        "resourceId": "5",
        "details": "{\"page\":0,\"size\":10}",
        "ipAddress": "192.168.1.1",
        "userAgent": "Mozilla/5.0...",
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

#### Get Activity Log by ID

```
GET /api/v1/admin/activity-logs/{logId}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "adminId": 1,
    "adminUsername": "superadmin",
    "adminEmail": "admin@example.com",
    "action": "DELETE",
    "resourceType": "Client",
    "resourceId": "42",
    "details": "{\"clientId\":42,\"email\":\"user@example.com\",\"softDelete\":true}",
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0...",
    "createdAt": "2024-01-15T14:30:00"
  }
}
```

### Existing Token Management Endpoints

The following endpoints already exist in the system (see `AdminTokenManagementController`):

#### Password Reset Tokens

```
GET /api/v1/admin/tokens/password-reset        # List all
GET /api/v1/admin/tokens/password-reset/{id}   # Get by ID
DELETE /api/v1/admin/tokens/password-reset/{id} # Delete by ID
```

#### Verification Tokens

```
GET /api/v1/admin/tokens/verification          # List all
GET /api/v1/admin/tokens/verification/{id}     # Get by ID
DELETE /api/v1/admin/tokens/verification/{id}  # Delete by ID
```

## Action Types

The system logs the following action types:

| Action | Description |
|--------|-------------|
| READ | Viewing/reading data |
| CREATE | Creating new resources |
| UPDATE | Modifying existing resources |
| DELETE | Removing resources |

## Resource Types

Common resource types logged:

| Resource Type | Description |
|---------------|-------------|
| Admin | Admin user management |
| Coach | Coach management |
| Client | Client management |
| Connection | Coach-Client relationships |
| Goal | Goal management |
| BookUp | BookUp content management |
| EQAssessment | EQ assessment data |
| DriveDynamics | Drive Dynamics assessment data |
| LifeWheel | Life Wheel assessment data |
| PasswordResetToken | Password reset token management |
| VerificationToken | Email verification token management |
| AdminActivityLog | Activity log viewing (self-referential) |

## Scheduled Tasks

### Token Cleanup Service

**Location:** `com.furtherup.app.services.scheduled.TokenCleanupScheduledService`

**Schedule:** Daily at 2:00 AM (cron: `0 0 2 * * *`)

**Process:**
1. Queries for all expired password reset tokens
2. Deletes expired password reset tokens in batches of 1000
3. Queries for all expired verification tokens
4. Deletes expired verification tokens in batches of 1000
5. Logs the cleanup results

**Manual Trigger:**
The service also provides a `manualCleanup()` method that can be called programmatically if immediate cleanup is needed.

## Files Created/Modified

### New Files

1. **AdminActivityLogController.java**
   - Location: `src/main/java/com/furtherup/app/controllers/`
   - Endpoints for viewing admin activity logs
   - Level 0 admin restriction

2. **TokenCleanupScheduledService.java**
   - Location: `src/main/java/com/furtherup/app/services/scheduled/`
   - Scheduled task for cleaning expired tokens
   - Runs daily at 2:00 AM

3. **21admin_activity_logs.postman_collection.json**
   - Location: `postman_files/`
   - Postman collection for testing activity log endpoints

### Existing Files (Already Implemented)

1. **AdminTokenManagementController.java** - Token CRUD endpoints
2. **AdminTokenManagementService.java** - Token management business logic
3. **AdminActivityLogService.java** - Activity log business logic
4. **AdminActivityLogDTO.java** - Activity log data transfer object
5. **AdminActivityLogListResponse.java** - Paginated response wrapper

## Security

### Access Control

| Endpoint Type | Required Role | Required Level |
|---------------|---------------|----------------|
| Activity Logs | ADMIN | Level 0 (Super Admin) |
| Token Management | ADMIN | Level 0 (Super Admin) |

### Authorization Flow

1. Request includes JWT token in Authorization header
2. `@PreAuthorize("hasRole('ADMIN')")` validates admin role
3. Controller extracts admin ID from JWT
4. Controller checks `admin.getLevel() == 0`
5. Returns 403 Forbidden if level check fails

## Configuration

No additional configuration is required. The scheduled task uses Spring's `@Scheduled` annotation and requires `@EnableScheduling` on the application (already enabled via `FurtherApiApplication.java`).

## Example Usage

### Filter Activity Logs by Admin

```bash
curl -X GET "http://localhost:8080/api/v1/admin/activity-logs?adminId=1&page=0&size=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Filter by Action Type

```bash
curl -X GET "http://localhost:8080/api/v1/admin/activity-logs?action=DELETE&page=0&size=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Filter by Resource Type

```bash
curl -X GET "http://localhost:8080/api/v1/admin/activity-logs?resourceType=Coach&page=0&size=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Filter by Date Range

```bash
curl -X GET "http://localhost:8080/api/v1/admin/activity-logs?startDate=2024-01-01T00:00:00&page=0&size=20" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```
