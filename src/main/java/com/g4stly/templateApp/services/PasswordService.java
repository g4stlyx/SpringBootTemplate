package  com.g4stly.templateApp.services;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PasswordService {
    
    @Value("${app.security.pepper}")
    private String pepper;
    
    // Argon2 configuration from environment variables
    @Value("${app.security.argon2.memory-cost}")
    private int memoryCost;
    
    @Value("${app.security.argon2.time-cost}")
    private int timeCost;
    
    @Value("${app.security.argon2.parallelism}")
    private int parallelism;
    
    @Value("${app.security.argon2.salt-length}")
    private int saltLength;
    
    @Value("${app.security.argon2.hash-length}")
    private int hashLength;
    
    private Argon2 argon2;
    
    // Initialize Argon2 after properties are injected
    private Argon2 getArgon2() {
        if (argon2 == null) {
            argon2 = Argon2Factory.create(
                Argon2Factory.Argon2Types.ARGON2id,
                saltLength,
                hashLength
            );
        }
        return argon2;
    }
    
    // Generate a random salt
    public String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[saltLength];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    // Hash password with salt and pepper
    public String hashPassword(String password, String salt) {
        // Combine password with pepper first, then salt
        String passwordWithPepper = password + pepper;
        
        // Hash the password
        char[] passwordChars = (passwordWithPepper + salt).toCharArray();
        try {
            return getArgon2().hash(timeCost, memoryCost, parallelism, passwordChars);
        } finally {
            // Clear the password from memory for security
            for (int i = 0; i < passwordChars.length; i++) {
                passwordChars[i] = 0;
            }
        }
    }
    
    // Verify password
    public boolean verifyPassword(String password, String salt, String hash) {
        // Combine password with pepper first, then salt
        String passwordWithPepper = password + pepper;
        
        // Verify the password
        char[] passwordChars = (passwordWithPepper + salt).toCharArray();
        try {
            return getArgon2().verify(hash, passwordChars);
        } finally {
            // Clear the password from memory
            for (int i = 0; i < passwordChars.length; i++) {
                passwordChars[i] = 0;
            }
        }
    }
}