package backend.common.util;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class WarmupScheduler {

    // Azure F1 free tier idles after ~20 min of HTTP inactivity.
    // We ping our own /api/health every 14 minutes to keep the instance warm.
    private static final String HEALTH_URL = "https://codesniff-backend.azurewebsites.net/api/health";
    private final RestTemplate restTemplate = new RestTemplate();

    @Scheduled(fixedRate = 840000) // every 14 minutes (safely under Azure's ~20 min idle timeout)
    public void keepAlive() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            String response = restTemplate.getForObject(HEALTH_URL, String.class);
            System.out.println("[CodeSniff] Keep-alive ping OK at " + timestamp + " → " + response);
        } catch (Exception e) {
            System.err.println("[CodeSniff] Keep-alive ping FAILED at " + timestamp + ": " + e.getMessage());
        }
    }
}
