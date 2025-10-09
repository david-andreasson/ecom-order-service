package se.moln.orderservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**") // Tillåt alla endpoints
                .allowedOrigins(
                        "http://localhost:8080", // frontend via Nginx i docker-compose
                        "http://localhost:3000",
                        "https://productservice.drillbi.se",
                        "https://userservice.drillbi.se",
                        "https://kebabrolle.drillbi.se",
                        "https://orderservice.drillbi.se"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}