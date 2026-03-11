package com.demo.devops.auditservice.config;

import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StartupValidation implements InitializingBean {
  private static final Set<String> PLACEHOLDER_VALUES = Set.of(
      "secret",
      "dev-password-placeholder",
      "dev-jwt-secret-placeholder",
      "dev-jwt-secret-placeholder-32bytes",
      "dev-mailer-key-placeholder",
      "dev-notify-key-placeholder",
      "dev-audit-key-placeholder",
      "your-smtp-user",
      "your-smtp-password",
      "your-smtp-from@example.com");

  private final String datasourcePassword;
  private final String auditApiKey;
  private final String currentJwtSecret;
  private final String previousJwtSecret;

  public StartupValidation(
      @Value("${spring.datasource.password:}") String datasourcePassword,
      @Value("${audit.api-key:}") String auditApiKey,
      @Value("${app.jwt.current-secret:${app.jwt.secret:}}") String currentJwtSecret,
      @Value("${app.jwt.previous-secret:}") String previousJwtSecret) {
    this.datasourcePassword = datasourcePassword;
    this.auditApiKey = auditApiKey;
    this.currentJwtSecret = currentJwtSecret;
    this.previousJwtSecret = previousJwtSecret;
  }

  @Override
  public void afterPropertiesSet() {
    requireSecret("SPRING_DATASOURCE_PASSWORD", datasourcePassword);
    requireSecret("AUDIT_API_KEY", auditApiKey);
    requireSecret("APP_JWT_CURRENT_SECRET", currentJwtSecret);
    if (previousJwtSecret != null && !previousJwtSecret.isBlank()) {
      requireSecret("APP_JWT_PREVIOUS_SECRET", previousJwtSecret);
    }
  }

  private static void requireSecret(String name, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required and cannot be blank");
    }
    String normalized = value.trim();
    if (isPlaceholder(normalized)) {
      throw new IllegalStateException(name + " uses a placeholder value and must be replaced");
    }
  }

  private static boolean isPlaceholder(String value) {
    String normalized = value.toLowerCase();
    return PLACEHOLDER_VALUES.contains(normalized)
        || normalized.contains("placeholder")
        || normalized.startsWith("your-")
        || normalized.contains("example.com")
        || normalized.contains("replace-with");
  }
}
