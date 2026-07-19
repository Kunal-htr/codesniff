package backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/analyze",
                    "/api/analyze-files",
                    "/api/report/**",
                    "/api/batch/**",
                    "/api/health",
                    "/api/info",
                    "/api/auth/**",
                    "/",
                    "/*.html",
                    "/*.js",
                    "/*.css",
                    "/*.png",
                    "/*.ico",
                    "/*.json",
                    "/assets/**"
                ).permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
