package  com.g4stly.templateApp.services;

import  com.g4stly.templateApp.dto.two_factor.TwoFactorSetupResponse;
import  com.g4stly.templateApp.exception.ResourceNotFoundException;
import  com.g4stly.templateApp.models.Admin;
import  com.g4stly.templateApp.repos.AdminRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.HmacHashFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing Two-Factor Authentication (TOTP) for admin users.
 * Uses Google Authenticator compatible TOTP algorithm.
 */
@Service
public class TwoFactorAuthService {

    @Autowired
    private AdminRepository adminRepository;

    @Value("${app.name:Template App}")
    private String appName;

    private final GoogleAuthenticator googleAuthenticator;

    public TwoFactorAuthService() {
        // Configure GoogleAuthenticator for maximum compatibility with Authy and other apps
        GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder configBuilder =
                new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                        .setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(30))  // 30-second time window
                        .setWindowSize(5)  // Allow 5 time steps before/after for clock drift
                        .setCodeDigits(6)  // 6-digit codes (standard)
                        .setHmacHashFunction(HmacHashFunction.HmacSHA1);  // SHA1 (most compatible)

        this.googleAuthenticator = new GoogleAuthenticator(configBuilder.build());
    }

    /**
     * Generate a new 2FA secret for an admin
     * @param adminId The admin's ID
     * @return TwoFactorSetupResponse containing secret, QR code, and manual entry key
     */
    public TwoFactorSetupResponse generateSecret(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        // Generate new secret key
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        String secret = key.getKey();

        // Save the secret (but don't enable 2FA yet - wait for verification)
        admin.setTwoFactorSecret(secret);
        adminRepository.save(admin);

        // Generate OTP Auth URL manually for better Authy compatibility
        // Format: otpauth://totp/Issuer:AccountName?secret=SECRET&issuer=Issuer&algorithm=SHA1&digits=6&period=30
        String accountName = admin.getUsername();
        String issuer = appName;
        String qrCodeUrl = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                issuer.replace(":", "").replace(" ", "%20"),  // URL encode issuer
                accountName,
                secret,
                issuer.replace(":", "").replace(" ", "%20")   // URL encode issuer
        );

        // Generate QR code image as base64
        String qrCodeImage = generateQRCodeImage(qrCodeUrl);

        return new TwoFactorSetupResponse(secret, qrCodeImage, secret);
    }

    /**
     * Verify the TOTP code and enable 2FA for the admin
     * @param adminId The admin's ID
     * @param code The 6-digit verification code
     * @return true if code is valid and 2FA was enabled
     */
    public boolean verifyAndEnable(Long adminId, String code) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getTwoFactorSecret() == null) {
            throw new IllegalStateException("2FA secret not generated. Please setup 2FA first.");
        }

        int numericCode;
        try {
            numericCode = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return false;
        }

        boolean isValid = googleAuthenticator.authorize(admin.getTwoFactorSecret(), numericCode);

        if (isValid) {
            admin.setTwoFactorEnabled(true);
            adminRepository.save(admin);
        }

        return isValid;
    }

    /**
     * Verify TOTP code for login
     * @param adminId The admin's ID
     * @param code The 6-digit verification code
     * @return true if code is valid
     */
    public boolean verifyCode(Long adminId, String code) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (!Boolean.TRUE.equals(admin.getTwoFactorEnabled()) || admin.getTwoFactorSecret() == null) {
            throw new IllegalStateException("2FA is not enabled for this admin");
        }

        int numericCode;
        try {
            numericCode = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return false;
        }

        return googleAuthenticator.authorize(admin.getTwoFactorSecret(), numericCode);
    }

    /**
     * Verify TOTP code for login by username
     * @param username The admin's username
     * @param code The 6-digit verification code
     * @return true if code is valid
     */
    public boolean verifyCodeByUsername(String username, String code) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (!Boolean.TRUE.equals(admin.getTwoFactorEnabled()) || admin.getTwoFactorSecret() == null) {
            throw new IllegalStateException("2FA is not enabled for this admin");
        }

        int numericCode;
        try {
            numericCode = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return false;
        }

        return googleAuthenticator.authorize(admin.getTwoFactorSecret(), numericCode);
    }

    /**
     * Disable 2FA for an admin (requires verification code)
     * @param adminId The admin's ID
     * @param code The 6-digit verification code
     */
    public void disable(Long adminId, String code) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        // Verify code before disabling
        if (!verifyCode(adminId, code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        admin.setTwoFactorEnabled(false);
        admin.setTwoFactorSecret(null);
        adminRepository.save(admin);
    }

    /**
     * Check if 2FA is enabled for an admin
     * @param adminId The admin's ID
     * @return true if 2FA is enabled
     */
    public boolean isTwoFactorEnabled(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        return Boolean.TRUE.equals(admin.getTwoFactorEnabled());
    }

    /**
     * Check if 2FA is enabled for an admin by username
     * @param username The admin's username
     * @return true if 2FA is enabled
     */
    public boolean isTwoFactorEnabledByUsername(String username) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        return Boolean.TRUE.equals(admin.getTwoFactorEnabled());
    }

    /**
     * Get admin by username (for 2FA login flow)
     * @param username The admin's username
     * @return Admin entity
     */
    public Admin getAdminByUsername(String username) {
        return adminRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
    }

    /**
     * Generate QR code image as base64 string
     * @param barcodeText The OTP auth URL
     * @return Base64 encoded PNG image with data URI prefix
     */
    private String generateQRCodeImage(String barcodeText) {
        try {
            QRCodeWriter barcodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = barcodeWriter.encode(barcodeText, BarcodeFormat.QR_CODE, 250, 250);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] imageBytes = outputStream.toByteArray();

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}
