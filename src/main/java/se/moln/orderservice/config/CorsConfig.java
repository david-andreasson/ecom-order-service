package se.moln.orderservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry; // Korrekt import
import org.springframework.web.reactive.config.WebFluxConfigurer; // Korrekt import

@Configuration
public class CorsConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Tillåt alla endpoints
                .allowedOrigins("http://localhost:3000", "https://productservice.drillbi.se/", "https://userservice.drillbi.se", "https://kebabrolle.drillbi.se/", "https://orderservice.drillbi.se/")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}