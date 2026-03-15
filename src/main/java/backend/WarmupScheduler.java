package backend;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class WarmupScheduler {

    @Scheduled(fixedRate = 900000) // every 15 minutes
    public void keepAlive() {
        System.out.println("[CodeSniff] Server keepalive ping at: " +
                LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                )
        );
    }
}