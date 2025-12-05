package  com.g4stly.templateApp.services;

import  com.g4stly.templateApp.config.CloudflareR2Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    private final S3Client cloudflareR2Client;
    private final CloudflareR2Config r2Config;

    // Allowed image types
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    // Max file size: 5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * Upload profile image for client, coach, or admin
     */
    public String uploadProfileImage(MultipartFile file, String userType, Long userId) throws IOException {
        validateImage(file);
        
        // Normalize userType to avoid Turkish character issues (ı vs i)
        String normalizedUserType = normalizeUserType(userType);
        
        String fileName = generateFileName(file, "profile", normalizedUserType, userId);
        String key = "profiles/" + normalizedUserType + "/" + fileName;
        
        uploadToR2(file, key);
        
        return getPublicUrl(key);
    }

    /**
     * Upload book cover image
     */
    public String uploadBookCover(MultipartFile file, Long bookId) throws IOException {
        validateImage(file);
        
        String fileName = generateFileName(file, "book", null, bookId);
        String key = "books/" + fileName;
        
        uploadToR2(file, key);
        
        return getPublicUrl(key);
    }

    /**
     * Delete image by URL
     */
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        try {
            // Extract key from URL
            String key = extractKeyFromUrl(imageUrl);
            
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(r2Config.getBucketName())
                    .key(key)
                    .build();
            
            cloudflareR2Client.deleteObject(deleteRequest);
            log.info("Deleted image from R2: {}", key);
        } catch (Exception e) {
            log.error("Error deleting image from R2: {}", imageUrl, e);
            throw new RuntimeException("Resim silinemedi: " + e.getMessage());
        }
    }

    /**
     * Update profile image (delete old, upload new)
     */
    public String updateProfileImage(MultipartFile file, String userType, Long userId, String oldImageUrl) throws IOException {
        // Delete old image if exists
        if (oldImageUrl != null && !oldImageUrl.isEmpty()) {
            deleteImage(oldImageUrl);
        }
        
        // Normalize userType before upload
        String normalizedUserType = normalizeUserType(userType);
        
        // Upload new image
        return uploadProfileImage(file, normalizedUserType, userId);
    }

    /**
     * Update book cover (delete old, upload new)
     */
    public String updateBookCover(MultipartFile file, Long bookId, String oldImageUrl) throws IOException {
        // Delete old image if exists
        if (oldImageUrl != null && !oldImageUrl.isEmpty()) {
            deleteImage(oldImageUrl);
        }
        
        // Upload new image
        return uploadBookCover(file, bookId);
    }

    /**
     * Validate image file
     */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Dosya boş olamaz");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Dosya boyutu 5MB'dan büyük olamaz");
        }

        // Check content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Sadece JPG, PNG ve WEBP formatları desteklenir");
        }
    }

    /**
     * Upload file to R2
     */
    private void uploadToR2(MultipartFile file, String key) throws IOException {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(r2Config.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            cloudflareR2Client.putObject(putRequest, 
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            
            log.info("Uploaded image to R2: {}", key);
        } catch (S3Exception e) {
            log.error("Error uploading to R2: {}", key, e);
            throw new RuntimeException("Resim yüklenemedi: " + e.getMessage());
        }
    }

    /**
     * Generate unique file name
     */
    private String generateFileName(MultipartFile file, String prefix, String userType, Long entityId) {
        String originalFileName = file.getOriginalFilename();
        String extension = "";
        
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }

        String uuid = UUID.randomUUID().toString();
        
        if (userType != null) {
            return String.format("%s_%s_%d_%s%s", prefix, userType, entityId, uuid, extension);
        } else {
            return String.format("%s_%d_%s%s", prefix, entityId, uuid, extension);
        }
    }

    /**
     * Get public URL for uploaded image
     */
    private String getPublicUrl(String key) {
        // Return public URL: https://your-domain.com/key
        return r2Config.getPublicDomain() + "/" + key;
    }

    /**
     * Extract key from public URL
     */
    private String extractKeyFromUrl(String url) {
        String domain = r2Config.getPublicDomain();
        if (url.startsWith(domain)) {
            return url.substring(domain.length() + 1); // +1 for the slash
        }
        throw new IllegalArgumentException("URL geçersiz");
    }

    /**
     * Normalize user type to avoid Turkish character issues
     * Converts to lowercase using English locale to prevent ı/i problems
     */
    private String normalizeUserType(String userType) {
        if (userType == null) {
            return null;
        }
        // Use English locale to ensure 'I' becomes 'i' not 'ı'
        return userType.toLowerCase(java.util.Locale.ENGLISH);
    }
}
