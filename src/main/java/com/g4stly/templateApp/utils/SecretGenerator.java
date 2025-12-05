package  com.g4stly.templateApp.utils;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class to generate secure secrets for JWT and password hashing
 * Run this class once to generate secrets, then copy them to your .env file
 */
public class SecretGenerator {
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Generate a cryptographically secure random string
     * @param byteLength Length in bytes (will be base64 encoded, so actual string will be longer)
     * @return Base64 encoded random string
     */
    public static String generateSecret(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }
    
    /**
     * Generate a secure hex string
     * @param byteLength Length in bytes
     * @return Hex encoded random string
     */
    public static String generateHexSecret(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : randomBytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
    
    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("SECURE SECRETS GENERATOR FOR Template APP");
        System.out.println("=".repeat(80));
        System.out.println();
        
        // Generate JWT Secret (256-bit for RS256)
        String jwtSecret = generateSecret(64); // 64 bytes = 512 bits
        System.out.println("JWT_SECRET (512-bit, Base64 encoded):");
        System.out.println(jwtSecret);
        System.out.println();
        
        // Generate Pepper for password hashing (256-bit)
        String pepper = generateSecret(32); // 32 bytes = 256 bits
        System.out.println("PEPPER (256-bit, Base64 encoded):");
        System.out.println(pepper);
        System.out.println();
        
        // Alternative: Hex encoded versions
        String jwtSecretHex = generateHexSecret(64);
        String pepperHex = generateHexSecret(32);
        
        System.out.println("-".repeat(80));
        System.out.println("ALTERNATIVE: HEX ENCODED VERSIONS");
        System.out.println("-".repeat(80));
        System.out.println();
        
        System.out.println("JWT_SECRET (512-bit, Hex encoded):");
        System.out.println(jwtSecretHex);
        System.out.println();
        
        System.out.println("PEPPER (256-bit, Hex encoded):");
        System.out.println(pepperHex);
        System.out.println();
        
        System.out.println("=".repeat(80));
        System.out.println("INSTRUCTIONS:");
        System.out.println("=".repeat(80));
        System.out.println("1. Copy the secrets above");
        System.out.println("2. Update your .env file with these values");
        System.out.println("3. IMPORTANT: Keep these secrets safe and NEVER commit them to git!");
        System.out.println("4. Restart your application after updating .env");
        System.out.println("5. You'll need to re-register users as old passwords won't work with new pepper");
        System.out.println("=".repeat(80));
    }
}
