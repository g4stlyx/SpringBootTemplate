# Profile Management System Documentation

## Overview
Complete profile management system for coaches and clients to view and update their profile information.

## Architecture

### DTOs
- **CoachProfileDTO**: Full profile data (own view)
- **UpdateCoachProfileDTO**: Editable fields for profile updates
- **CoachPublicProfileDTO**: Public profile view (for clients)
- **ClientProfileDTO**: Full profile data (own view)
- **UpdateClientProfileDTO**: Editable fields for profile updates

### Services
- **CoachProfileService**: Business logic for coach profiles
- **ClientProfileService**: Business logic for client profiles

### Controllers
- **CoachProfileController**: REST endpoints for coach profiles
- **ClientProfileController**: REST endpoints for client profiles

---

## Coach Profile Endpoints

### 1. Get Own Profile
**Endpoint**: `GET /api/v1/coach/profile`  
**Auth**: Coach role required  
**Description**: Returns coach's complete profile information

**Response**:
```json
{
  "id": 1,
  "username": "coach_john",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "profilePicture": "https://r2.example.com/profiles/coach/1.jpg",
  "phone": "+90 555 123 4567",
  "bio": "Professional life coach with 10 years of experience...",
  "specializations": ["Career Coaching", "Executive Coaching"],
  "certifications": ["ICF-ACC", "NLP Practitioner"],
  "yearsOfExperience": 10,
  "hourlyRate": 150.00,
  "isVerified": true,
  "emailVerified": true,
  "createdAt": "2024-01-15 10:30:00",
  "updatedAt": "2024-10-29 15:45:00"
}
```

---

### 2. Update Profile
**Endpoint**: `PUT /api/v1/coach/profile`  
**Auth**: Coach role required  
**Description**: Updates coach's editable profile fields

**Request Body**:
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+90 555 123 4567",
  "bio": "Updated bio...",
  "specializations": ["Career Coaching", "Executive Coaching", "Team Coaching"],
  "certifications": ["ICF-ACC", "NLP Practitioner"],
  "yearsOfExperience": 11,
  "hourlyRate": 175.00
}
```

**Response**: Same as Get Own Profile (updated data)

**Validations**:
- All fields are optional
- `hourlyRate` must be positive if provided
- `yearsOfExperience` must be non-negative

---

### 3. Get Public Profile
**Endpoint**: `GET /api/v1/coach/profile/public/{coachId}`  
**Auth**: Client, Coach, or Admin role required  
**Description**: Returns coach's public profile (as clients see it)

**Response**:
```json
{
  "id": 1,
  "firstName": "John",
  "lastName": "Doe",
  "profilePicture": "https://r2.example.com/profiles/coach/1.jpg",
  "bio": "Professional life coach with 10 years of experience...",
  "specializations": ["Career Coaching", "Executive Coaching"],
  "certifications": ["ICF-ACC", "NLP Practitioner"],
  "yearsOfExperience": 10,
  "hourlyRate": 150.00,
  "isVerified": true,
  "activeClientCount": 5,
  "memberSince": "2024-01-15"
}
```

**Note**: 
- `activeClientCount` shows number of current ACTIVE coaching relationships
- Does not include sensitive information (email, phone, username)

---

## Client Profile Endpoints

### 1. Get Own Profile
**Endpoint**: `GET /api/v1/client/profile`  
**Auth**: Client role required  
**Description**: Returns client's complete profile information

**Response**:
```json
{
  "id": 1,
  "username": "jane_client",
  "email": "jane@example.com",
  "firstName": "Jane",
  "lastName": "Smith",
  "profilePicture": "https://r2.example.com/profiles/client/1.jpg",
  "phone": "+90 555 987 6543",
  "dateOfBirth": "1990-05-15",
  "bio": "Looking to improve my career and personal growth",
  "occupation": "Software Engineer",
  "emailVerified": true,
  "onboardingCompleted": true,
  "createdAt": "2024-02-20 14:20:00",
  "updatedAt": "2024-10-29 16:00:00",
  "lastLoginAt": "2024-10-29 16:00:00"
}
```

---

### 2. Update Profile
**Endpoint**: `PUT /api/v1/client/profile`  
**Auth**: Client role required  
**Description**: Updates client's editable profile fields

**Request Body**:
```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "phone": "+90 555 987 6543",
  "dateOfBirth": "1990-05-15",
  "bio": "Updated bio...",
  "occupation": "Senior Software Engineer"
}
```

**Response**: Same as Get Own Profile (updated data)

**Validations**:
- All fields are optional
- `dateOfBirth` must be in format: `yyyy-MM-dd` (e.g., "1990-05-15")
- If invalid date format, returns error: "Geçersiz tarih formatı. yyyy-MM-dd formatında olmalı."

---

## Profile Picture Integration

Profile pictures are managed through the existing Image Upload System (see `8IMAGE_UPLOAD_SYSTEM.md`).

### Update Profile Picture Flow

1. **Upload Image**:
   ```
   POST /api/v1/image/upload/profile
   Content-Type: multipart/form-data
   
   file: [image file]
   ```
   
   Response:
   ```json
   {
     "success": true,
     "message": "Profil resmi başarıyla yüklendi",
     "imageUrl": "https://r2.example.com/profiles/coach/1.jpg"
   }
   ```

2. **The image upload automatically updates the profile picture** in the database

3. **Get updated profile**:
   - Coach: `GET /api/v1/coach/profile`
   - Client: `GET /api/v1/client/profile`

**Note**: Profile picture updates are handled automatically by the Image Upload Service, so there's no separate endpoint needed.

---

## Error Handling

### Common Errors

**Coach Not Found (404)**:
```json
{
  "timestamp": "2024-10-29T16:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Belirtilen ID ile coach bulunamadı: 999"
}
```

**Client Not Found (404)**:
```json
{
  "timestamp": "2024-10-29T16:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Belirtilen ID ile client bulunamadı: 999"
}
```

**Invalid Date Format (400)**:
```json
{
  "timestamp": "2024-10-29T16:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Geçersiz tarih formatı. yyyy-MM-dd formatında olmalı."
}
```

**Unauthorized (403)**:
```json
{
  "timestamp": "2024-10-29T16:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied"
}
```

---

## Security

### Role-Based Access Control

**Coach Endpoints**:
- `GET /api/v1/coach/profile` - Requires `COACH` role
- `PUT /api/v1/coach/profile` - Requires `COACH` role
- `GET /api/v1/coach/profile/public/{coachId}` - Requires `CLIENT`, `COACH`, or `ADMIN` role

**Client Endpoints**:
- `GET /api/v1/client/profile` - Requires `CLIENT` role
- `PUT /api/v1/client/profile` - Requires `CLIENT` role

### JWT Authentication

All endpoints require a valid JWT token in the Authorization header:
```
Authorization: Bearer <jwt_token>
```

The user ID is automatically extracted from the JWT token, ensuring users can only access/modify their own profiles.

---

## Database Fields

### Coach Profile Fields

**Displayed in Own Profile**:
- id, username, email, firstName, lastName, profilePicture, phone
- bio, specializations, certifications, yearsOfExperience, hourlyRate
- isVerified, emailVerified, createdAt, updatedAt

**Editable Fields**:
- firstName, lastName, phone, bio
- specializations, certifications, yearsOfExperience, hourlyRate

**Public Profile Only**:
- activeClientCount (calculated dynamically)
- memberSince (formatted from createdAt)

### Client Profile Fields

**Displayed in Own Profile**:
- id, username, email, firstName, lastName, profilePicture, phone
- dateOfBirth, bio, occupation
- emailVerified, onboardingCompleted, createdAt, updatedAt, lastLoginAt

**Editable Fields**:
- firstName, lastName, phone, dateOfBirth, bio, occupation

---

## Implementation Details

### Service Layer

**CoachProfileService**:
- `getMyProfile(Long coachId)` - Returns full profile DTO
- `updateProfile(Long coachId, UpdateCoachProfileDTO)` - Updates and returns updated DTO
- `getPublicProfile(Long coachId)` - Returns public view with active client count
- `updateProfilePicture(Long coachId, String imageUrl)` - Updates picture URL (called by ImageUploadService)

**ClientProfileService**:
- `getMyProfile(Long clientId)` - Returns full profile DTO
- `updateProfile(Long clientId, UpdateClientProfileDTO)` - Updates with date validation
- `updateProfilePicture(Long clientId, String imageUrl)` - Updates picture URL (called by ImageUploadService)

### Active Client Count Calculation

For coach public profiles, active client count is calculated by counting ACTIVE relationships:
```java
long activeCount = coachConnectionRepository
    .countByCoachAndStatus(coach, ConnectionStatus.ACTIVE);
```

### Date Handling

Client date of birth is parsed and validated:
```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
LocalDate parsedDate = LocalDate.parse(dateOfBirth, formatter);
```

---

## Testing with Postman

See `13profile_management.postman_collection.json` for complete API tests.

### Quick Test Flow

1. **Login as Coach**:
   ```
   POST /api/v1/auth/login
   {
     "username": "coach_john",
     "password": "password123"
   }
   ```

2. **Get Profile**:
   ```
   GET /api/v1/coach/profile
   Authorization: Bearer <token>
   ```

3. **Update Profile**:
   ```
   PUT /api/v1/coach/profile
   Authorization: Bearer <token>
   {
     "bio": "Updated bio",
     "hourlyRate": 200.00
   }
   ```

4. **Login as Client and View Coach**:
   ```
   GET /api/v1/coach/profile/public/1
   Authorization: Bearer <client_token>
   ```

---

## Future Enhancements

### Client Privacy Settings (Planned)
- Profile visibility settings
- Hide profile from search
- Private/public profile toggle
- Control what coaches can see

### Additional Features (Potential)
- Profile completion percentage
- Profile badges/achievements
- Reviews and ratings integration
- Social media links
- Availability calendar preview

---

## Related Documentation

- `0AUTH_SYSTEM.md` - Authentication and JWT tokens
- `3.1COACH_CLIENT_CONNECTION_DESIGN.md` - Relationship management
- `8IMAGE_UPLOAD_SYSTEM.md` - Profile picture uploads
- `9EMAIL_NOTIFICATION_SYSTEM.md` - Email notifications for profile updates

---

## Status

✅ **COMPLETED** - Full implementation with documentation and tests

**Created**: 2024-10-29  
**Last Updated**: 2024-10-29  
**Version**: 1.0.0
