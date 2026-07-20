package backend.modules.user;

import backend.common.exception.EmailNotVerifiedException;
import backend.common.exception.InvalidCredentialsException;
import backend.common.exception.UserAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials."));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials.");
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email before logging in.");
        }

        return user;
    }

    public void registerUser(RegisterRequestDTO request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("A user with this email already exists.");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(false);

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiresAt(OffsetDateTime.now().plusHours(24));

        userRepository.save(user);

        try {
            emailService.sendVerificationEmail(user.getEmail(), token);
        } catch (Exception e) {
            log.error("Failed to send verification email during registration for {}", user.getEmail(), e);
            // Registration still succeeds; user can request a new verification email later.
        }
    }

    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token."));

        if (user.getVerificationTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Verification token has expired.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);

        userRepository.save(user);
    }

    public void resendVerificationEmail(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            
            if (user.isEmailVerified()) {
                return; // Do nothing, avoid revealing state
            }
            
            String token = UUID.randomUUID().toString();
            user.setVerificationToken(token);
            user.setVerificationTokenExpiresAt(OffsetDateTime.now().plusHours(24));
            
            userRepository.save(user);

            try {
                emailService.sendVerificationEmail(user.getEmail(), token);
            } catch (Exception e) {
                log.error("Failed to resend verification email for {}", user.getEmail(), e);
            }
        }
        // If the user doesn't exist, we do nothing and return normally (preventing email enumeration)
    }

    public void forgotPassword(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiresAt(OffsetDateTime.now().plusHours(1));
            
            userRepository.save(user);

            try {
                emailService.sendPasswordResetEmail(user.getEmail(), token);
            } catch (Exception e) {
                log.error("Failed to send password reset email for {}", user.getEmail(), e);
            }
        }
    }

    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));

        if (user.getResetTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Reset token has expired.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);

        userRepository.save(user);
    }
}
