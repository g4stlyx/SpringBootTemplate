package  com.g4stly.templateApp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    // Async ve Scheduling özelliklerini etkinleştirir
    // EmailService ve EmailEventListener'daki @Async metodları için gerekli
    // @Scheduled metodları için gerekli (hedef tarihi hatırlatmaları)
}
