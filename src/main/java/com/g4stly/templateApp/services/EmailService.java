package  com.g4stly.templateApp.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

//TODO: this service should work on async mode to prevent blocking main thread. Event listeners, queues or other async mechanisms can be used.

@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url}")
    private String frontendUrl;
    
    @Value("${app.admin.email:admin@g4stly.tr}")
    private String adminEmail;

    public void sendVerificationEmail(String to, String token, String name) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Email Verification");

            // Use the built-in verification page
            String verificationUrl = frontendUrl + "/verify?token=" + token;

            // Create a Thymeleaf context
            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("verificationUrl", verificationUrl);

            // Process the HTML template with Thymeleaf
            String htmlContent = templateEngine.process("verification-email", context);

            // Set the email content
            helper.setText(htmlContent, true);

            // Send the email
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // Log error but don't prevent registration
            System.err.println("Failed to send verification email: " + e.getMessage());
        }
    }

    public void sendPasswordResetEmail(String to, String token, String name) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Password Reset Request");

            // Use the built-in reset password page
            String resetUrl = frontendUrl + "/reset-password?token=" + token;

            // Create a Thymeleaf context
            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("expiryMinutes", 15); // Match your token expiry time

            // Process the HTML template with Thymeleaf
            String htmlContent = templateEngine.process("password-reset-email", context);

            // Set the email content
            helper.setText(htmlContent, true);

            // Send the email
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // Log error but don't fail the whole process
            System.err.println("Failed to send password reset email: " + e.getMessage());
        }
    }

    public void sendPasswordResetSuccessEmail(String to, String name, String ipAddress) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Şifreniz Başarıyla Değiştirildi - KSS");

            // Create a Thymeleaf context
            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("timestamp", java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm", 
                java.util.Locale.forLanguageTag("tr-TR"))));
            context.setVariable("currentDate", java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy", 
                java.util.Locale.forLanguageTag("tr-TR"))));
            context.setVariable("ipAddress", ipAddress != null ? ipAddress : "Bilinmiyor");

            // Process the HTML template with Thymeleaf
            String htmlContent = templateEngine.process("password-reset-success-email", context);

            // Set the email content
            helper.setText(htmlContent, true);

            // Send the email
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // Log error but don't fail the whole process
            System.err.println("Failed to send password reset success email: " + e.getMessage());
        }
    }

    /**
     * Sends a system notification email to the administrator
     * This is used for automated alerts and monitoring notifications
     *
     * @param subject The email subject
     * @param body The HTML body content of the email
     */
    public void sendSystemNotificationEmail(String subject, String body) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(adminEmail);
            helper.setSubject(subject);
            helper.setText(body, true); // true indicates HTML content

            mailSender.send(mimeMessage);
            System.out.println("System notification email sent successfully to admin");
        } catch (MessagingException e) {
            System.err.println("Failed to send system notification email: " + e.getMessage());
        }
    }

}
