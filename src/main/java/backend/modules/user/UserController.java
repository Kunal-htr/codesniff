package backend.modules.user;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import backend.config.RateLimitingService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final boolean cookieSecure;
    private final RateLimitingService rateLimitingService;

    public UserController(UserService userService, JwtUtil jwtUtil, @Value("${app.cookie.secure}") boolean cookieSecure, RateLimitingService rateLimitingService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.cookieSecure = cookieSecure;
        this.rateLimitingService = rateLimitingService;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }
        
        return userService.getUserByEmail(principal.getName())
                .<ResponseEntity<Map<String, Object>>>map(user -> ResponseEntity.ok(Map.of(
                        "authenticated", true,
                        "email", user.getEmail(),
                        "name", user.getName() == null ? "" : user.getName()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of("authenticated", false)));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {
        rateLimitingService.checkLoginLimit(getClientIp(httpRequest), request.getEmail());
        User user = userService.login(request.getEmail(), request.getPassword());
        String token = jwtUtil.generateToken(user.getEmail());

        ResponseCookie jwtCookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .path("/")
                .maxAge(3600) // 1 hour expiry matches JWT token expiration
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new LoginResponseDTO(user.getName(), user.getEmail()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        ResponseCookie jwtCookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .path("/")
                .maxAge(0) // Expire immediately
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(Map.of("message", "Logged out successfully."));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequestDTO request, HttpServletRequest httpRequest) {
        rateLimitingService.checkRegisterLimit(getClientIp(httpRequest));
        userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "User registered successfully. Please check your email to verify your account."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequestDTO request) {
        userService.verifyEmail(request.getToken());
        return ResponseEntity.ok(Map.of("message", "Email verified successfully."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody ResendVerificationRequestDTO request, HttpServletRequest httpRequest) {
        rateLimitingService.checkResendVerificationLimit(getClientIp(httpRequest), request.getEmail());
        userService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If that email is registered, a verification link has been sent."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request, HttpServletRequest httpRequest) {
        rateLimitingService.checkForgotPasswordLimit(getClientIp(httpRequest), request.getEmail());
        userService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If that email is registered, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        userService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password has been successfully reset."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request, 
            java.security.Principal principal
    ) {
        if (principal == null) {
            throw new backend.common.exception.UnauthorizedException("You must be logged in to change your password.");
        }
        
        String email = principal.getName();
        userService.changePassword(email, request.getCurrentPassword(), request.getNewPassword());
        
        return ResponseEntity.ok(Map.of("message", "Password has been successfully changed."));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @Valid @RequestBody UpdateProfileRequestDTO request,
            java.security.Principal principal
    ) {
        if (principal == null) {
            throw new backend.common.exception.UnauthorizedException("You must be logged in to update your profile.");
        }
        
        String currentEmail = principal.getName();
        boolean emailChanged = userService.updateProfile(currentEmail, request.getName(), request.getEmail());
        
        if (emailChanged) {
            ResponseCookie jwtCookie = ResponseCookie.from("jwt", "")
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite(cookieSecure ? "None" : "Lax")
                    .path("/")
                    .maxAge(0) // Expire immediately
                    .build();
                    
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                    .body(Map.of(
                        "message", "Profile updated. Since your email changed, please verify your new email and log in again.",
                        "emailChanged", true
                    ));
        }
        
        return ResponseEntity.ok(Map.of(
            "message", "Profile successfully updated.",
            "emailChanged", false
        ));
    }
}
