package com.aiantfarm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration cfg = new CorsConfiguration();
        List<String> allowedOriginPatterns = allowedOrigins.stream().filter(
                origin -> origin.contains("*")
        ).toList();
        List<String> explicitAllowedOrigins = allowedOrigins.stream().filter(
                origin -> !origin.contains("*")
        ).toList();
        if(!allowedOriginPatterns.isEmpty()) {
            // When allowing all origins, must not allow credentials
            cfg.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        if(!explicitAllowedOrigins.isEmpty()) {
          cfg.setAllowedOrigins(explicitAllowedOrigins);
        }

        cfg.setAllowedMethods(Arrays.asList(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        // Allow Authorization header for bearer/JWT
        cfg.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        // Expose any headers your frontend may need to read (optional)
        cfg.setExposedHeaders(Arrays.asList("Location"));
        cfg.setAllowCredentials(true); // if you plan to send cookies; safe with explicit origins

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
