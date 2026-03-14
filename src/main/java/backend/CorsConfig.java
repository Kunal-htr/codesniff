package backend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(
                                "http://localhost:3000",
                                "http://127.0.0.1:3000",
                                "http://localhost:5173",
                                "http://localhost:9090",
                                "https://codesniff.vercel.app",
                                "https://codesniff-backend.onrender.com",
                                "https://codesniff.tech",
                                "https://www.codesniff.tech"
                        )
                        .allowedMethods("POST", "GET", "OPTIONS", "PUT", "DELETE")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}