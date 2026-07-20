package backend.config;

import backend.common.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final ConcurrentHashMap<String, Bucket> cache = new ConcurrentHashMap<>();

    // 5 attempts per 15 minutes
    private final Bandwidth loginLimit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15)));
    
    // 3 attempts per 1 hour
    private final Bandwidth genericAuthLimit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofHours(1)));

    private Bucket getBucket(String key, Bandwidth limit) {
        return cache.computeIfAbsent(key, k -> Bucket.builder().addLimit(limit).build());
    }

    public void checkLoginLimit(String ip, String email) {
        Bucket ipBucket = getBucket("login_ip_" + ip, loginLimit);
        Bucket emailBucket = getBucket("login_email_" + email, loginLimit);

        if (!ipBucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many login attempts from this IP. Please try again in 15 minutes.");
        }
        if (!emailBucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many login attempts for this email. Please try again in 15 minutes.");
        }
    }

    public void checkRegisterLimit(String ip) {
        Bucket ipBucket = getBucket("register_ip_" + ip, genericAuthLimit);
        if (!ipBucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many registration attempts from this IP. Please try again in 1 hour.");
        }
    }

    public void checkResendVerificationLimit(String ip, String email) {
        Bucket ipBucket = getBucket("verify_ip_" + ip, genericAuthLimit);
        Bucket emailBucket = getBucket("verify_email_" + email, genericAuthLimit);
        
        if (!ipBucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many resend attempts from this IP. Please try again in 1 hour.");
        }
        if (!emailBucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many resend attempts for this email. Please try again in 1 hour.");
        }
    }

    public void checkForgotPasswordLimit(String ip, String email) {
        Bucket ipBucket = getBucket("forgot_ip_" + ip, genericAuthLimit);
        Bucket emailBucket = getBucket("forgot_email_" + email, genericAuthLimit);

        if (!ipBucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many password reset attempts from this IP. Please try again in 1 hour.");
        }
        if (!emailBucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many password reset attempts for this email. Please try again in 1 hour.");
        }
    }
}
