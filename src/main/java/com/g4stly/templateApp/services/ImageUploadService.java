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
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    private final S3Client cloudflareR2Client;
    private final CloudflareR2Config r2Config;

    // ==================== FILE TYPE DETECTION VIA MAGIC BYTES ====================

    // Magic byte signatures for allowed file types
    private static final byte[] MAGIC_JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] MAGIC_GIF87 = new byte[]{0x47, 0x49, 0x46, 0x38, 0x37, 0x61}; // GIF87a
    private static final byte[] MAGIC_GIF89 = new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61}; // GIF89a
    private static final byte[] MAGIC_BMP = new byte[]{0x42, 0x4D};                             // BM
    private static final byte[] MAGIC_RIFF = new byte[]{0x52, 0x49, 0x46, 0x46};                // RIFF (WebP container)
    private static final byte[] MAGIC_WEBP = new byte[]{0x57, 0x45, 0x42, 0x50};                // WEBP (at offset 8)

    /** Allowed file extensions as a secondary check */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "bmp", "webp"
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
     * Validate image file using magic byte detection for security.
     * Prevents Content-Type spoofing attacks where malicious files are uploaded
     * with fake headers. This is a critical security measure for production systems.
     */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Dosya boş olamaz");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Dosya boyutu 5MB'dan büyük olamaz");
        }

        // Primary security check: Detect file type via magic bytes (not spoofable)
        String detectedMimeType = detectFileType(file);
        if (detectedMimeType == null) {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            log.warn("File upload rejected: Invalid or disallowed file type for '{}'", filename);
            throw new IllegalArgumentException("Geçersiz dosya formatı. Sadece JPG, PNG, GIF, BMP ve WEBP formatları desteklenir");
        }

        log.debug("File validated successfully. Detected type: {}, File: {}", 
                 detectedMimeType, file.getOriginalFilename());
    }

    /**
     * Detects the actual file type by inspecting magic bytes (file signature).
     * Returns the real MIME type if the file is an allowed type, or null if not allowed.
     * This prevents Content-Type spoofing attacks where a malicious file is uploaded
     * with a fake Content-Type header.
     * 
     * Defense-in-depth approach:
     * 1. Validates file extension (secondary check)
     * 2. Reads actual file content and checks magic bytes (primary security check)
     * 
     * @param file The uploaded file to validate
     * @return The detected MIME type if allowed, null otherwise
     */
    private String detectFileType(MultipartFile file) {
        // Secondary check: validate file extension
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String extension = getFileExtension(filename).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                log.warn("Rejected file upload: disallowed extension '{}' for file '{}'", extension, filename);
                return null;
            }
        }

        // Primary check: read magic bytes from actual file content
        try (InputStream is = file.getInputStream()) {
            // Read up to 12 bytes (enough for all signatures we check)
            byte[] header = new byte[12];
            int bytesRead = is.read(header);
            if (bytesRead < 2) {
                log.warn("Rejected file upload: file too small (less than 2 bytes)");
                return null; // Too small to be a valid file
            }

            // Check signatures in order (most specific first)
            if (bytesRead >= 8 && startsWith(header, MAGIC_PNG)) {
                return "image/png";
            }
            if (bytesRead >= 3 && startsWith(header, MAGIC_JPEG)) {
                return "image/jpeg";
            }
            if (bytesRead >= 6 && (startsWith(header, MAGIC_GIF87) || startsWith(header, MAGIC_GIF89))) {
                return "image/gif";
            }
            if (bytesRead >= 12 && startsWith(header, MAGIC_RIFF) && regionMatches(header, 8, MAGIC_WEBP)) {
                return "image/webp";
            }
            if (bytesRead >= 2 && startsWith(header, MAGIC_BMP)) {
                return "image/bmp";
            }

            log.warn("Rejected file upload: magic bytes did not match any allowed type for file '{}'", filename);
            return null;

        } catch (IOException e) {
            log.error("Failed to read file content for type detection: {}", e.getMessage(), e);
            return null;
        }
    }

    /** Check if data starts with the given prefix bytes */
    private boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(
            Arrays.copyOfRange(data, 0, prefix.length), prefix
        );
    }

    /** Check if a region of data matches the given bytes at the specified offset */
    private boolean regionMatches(byte[] data, int offset, byte[] target) {
        if (data.length < offset + target.length) {
            return false;
        }
        return Arrays.equals(
            Arrays.copyOfRange(data, offset, offset + target.length), target
        );
    }

    /** Extract file extension from filename */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }

    /**
     * Upload file to R2 with verified content type
     */
    private void uploadToR2(MultipartFile file, String key) throws IOException {
        try {
            // Use detected content type, not the one from the request header (security)
            String contentType = detectFileType(file);
            if (contentType == null) {
                contentType = "application/octet-stream"; // Fallback (should never happen after validation)
            }

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(r2Config.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            cloudflareR2Client.putObject(putRequest, 
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            
            log.info("Uploaded image to R2: {} with content type: {}", key, contentType);
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
     * Extract key from public URL with path traversal protection.
     * Sanitizes and validates the extracted key to prevent directory traversal attacks.
     */
    private String extractKeyFromUrl(String url) {
        String domain = r2Config.getPublicDomain();
        if (!url.startsWith(domain)) {
            throw new IllegalArgumentException("URL geçersiz: domain eşleşmiyor");
        }
        
        String key = url.substring(domain.length() + 1); // +1 for the slash
        
        // Sanitize: remove path traversal sequences
        key = key.replaceAll("\\.\\./", "").replaceAll("\\.\\.\\\\", "");
        
        // Validate key format (should match expected patterns)
        // Valid patterns: profiles/{userType}/* or books/*
        if (!key.matches("^profiles/(client|coach|admin)/[^/]+$") && 
            !key.matches("^books/[^/]+$")) {
            log.warn("Invalid key format rejected: {}", key);
            throw new IllegalArgumentException("URL geçersiz: format uygun değil");
        }
        
        return key;
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
