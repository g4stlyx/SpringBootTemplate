package  com.g4stly.templateApp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for scheduled tasks.
 * Enables Spring's @Scheduled annotation support.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // No methods needed - @EnableScheduling does the work
}