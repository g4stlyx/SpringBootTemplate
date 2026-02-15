package  com.g4stly.templateApp.controllers;

import  com.g4stly.templateApp.security.JwtUtils;
import  com.g4stly.templateApp.services.AdminProfileService;
import  com.g4stly.templateApp.services.ImageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/image")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0Or1Or2()")
public class AdminImageController {

    private final ImageUploadService imageUploadService;
    private final AdminProfileService adminProfileService;
    private final JwtUtils jwtUtils;

    /**
     * Upload profile image for admin
     */
    @PostMapping("/profile")
    public ResponseEntity<?> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {
        try {
            Long adminId = jwtUtils.extractUserId(token.substring(7)).longValue();
            
            String imageUrl = imageUploadService.uploadProfileImage(file, "ADMIN", adminId);
            
            // Update the admin's profile picture in the database
            adminProfileService.updateProfilePicture(adminId, imageUrl);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profil resmi başarıyla yüklendi",
                    "imageUrl", imageUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error uploading admin profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Profil resmi yüklenirken hata oluştu: " + e.getMessage()
            ));
        }
    }

    /**
     * Update profile image for admin
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "oldImageUrl", required = false) String oldImageUrl,
            @RequestHeader("Authorization") String token) {
        try {
            Long adminId = jwtUtils.extractUserId(token.substring(7)).longValue();
            
            String imageUrl = imageUploadService.updateProfileImage(file, "ADMIN", adminId, oldImageUrl);
            
            // Update the admin's profile picture in the database
            adminProfileService.updateProfilePicture(adminId, imageUrl);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profil resmi başarıyla güncellendi",
                    "imageUrl", imageUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error updating admin profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Profil resmi güncellenirken hata oluştu: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete profile image for admin
     */
    @DeleteMapping("/profile")
    public ResponseEntity<?> deleteProfileImage(
            @RequestParam("imageUrl") String imageUrl,
            @RequestHeader("Authorization") String token) {
        try {
            Long adminId = jwtUtils.extractUserId(token.substring(7)).longValue();
            
            // Verify ownership - admin can only delete their own profile image
            if (!adminProfileService.verifyProfileImageOwnership(adminId, imageUrl)) {
                log.warn("Admin {} attempted to delete profile image they don't own: {}", adminId, imageUrl);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "Bu profil resmini silme yetkiniz yok"
                ));
            }
            
            imageUploadService.deleteImage(imageUrl);
            
            // Clear profile picture from database
            adminProfileService.updateProfilePicture(adminId, null);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profil resmi başarıyla silindi"
            ));
        } catch (Exception e) {
            log.error("Error deleting admin profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Profil resmi silinirken hata oluştu: " + e.getMessage()
            ));
        }
    }

    /**
     * Upload book cover image
     */
    @PostMapping("/book-cover/{bookId}")
    public ResponseEntity<?> uploadBookCover(
            @PathVariable Long bookId,
            @RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = imageUploadService.uploadBookCover(file, bookId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Kitap kapağı başarıyla yüklendi",
                    "imageUrl", imageUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error uploading book cover", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Kitap kapağı yüklenirken hata oluştu: " + e.getMessage()
            ));
        }
    }

    /**
     * Update book cover image
     */
    @PutMapping("/book-cover/{bookId}")
    public ResponseEntity<?> updateBookCover(
            @PathVariable Long bookId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "oldImageUrl", required = false) String oldImageUrl) {
        try {
            String imageUrl = imageUploadService.updateBookCover(file, bookId, oldImageUrl);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Kitap kapağı başarıyla güncellendi",
                    "imageUrl", imageUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error updating book cover", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Kitap kapağı güncellenirken hata oluştu: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete book cover image
     */
    @DeleteMapping("/book-cover")
    public ResponseEntity<?> deleteBookCover(@RequestParam("imageUrl") String imageUrl) {
        try {
            imageUploadService.deleteImage(imageUrl);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Kitap kapağı başarıyla silindi"
            ));
        } catch (Exception e) {
            log.error("Error deleting book cover", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Kitap kapağı silinirken hata oluştu: " + e.getMessage()
            ));
        }
    }
}
