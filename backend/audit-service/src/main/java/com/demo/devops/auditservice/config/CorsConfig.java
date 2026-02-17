package com.demo.devops.auditservice.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {
  private static List<String> parseAllowedOrigins(String allowedOrigins) {
    if (allowedOrigins == null || allowedOrigins.isBlank()) {
      throw new IllegalStateException("app.cors.allowed-origins must be explicitly set and cannot be blank");
    }

    String trimmed = allowedOrigins.trim();
    if ("*".equals(trimmed)) {
      throw new IllegalStateException("app.cors.allowed-origins wildcard '*' is not allowed");
    }

    String[] parts = trimmed.split(",");
    List<String> origins = new ArrayList<>();
    for (String part : parts) {
      String origin = part.trim();
      if (!origin.isEmpty()) {
        origins.add(origin);
      }
    }

    if (origins.isEmpty()) {
      throw new IllegalStateException("app.cors.allowed-origins must contain at least one origin");
    }
    return origins;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(parseAllowedOrigins(allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
