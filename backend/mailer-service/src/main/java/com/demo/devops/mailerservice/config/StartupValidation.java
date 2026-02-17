package com.demo.devops.mailerservice.config;

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

  private final String mailerApiKey;
  private final String smtpHost;
  private final String smtpUsername;
  private final String smtpPassword;
  private final String smtpFrom;

  public StartupValidation(
      @Value("${mailer.api-key:}") String mailerApiKey,
      @Value("${spring.mail.host:}") String smtpHost,
      @Value("${spring.mail.username:}") String smtpUsername,
      @Value("${spring.mail.password:}") String smtpPassword,
      @Value("${spring.mail.from:}") String smtpFrom) {
    this.mailerApiKey = mailerApiKey;
    this.smtpHost = smtpHost;
    this.smtpUsername = smtpUsername;
    this.smtpPassword = smtpPassword;
    this.smtpFrom = smtpFrom;
  }

  @Override
  public void afterPropertiesSet() {
    requireSecret("MAILER_API_KEY", mailerApiKey);
    requireSecret("SPRING_MAIL_HOST", smtpHost);
    requireSecret("SPRING_MAIL_USERNAME", smtpUsername);
    requireSecret("SPRING_MAIL_PASSWORD", smtpPassword);
    requireSecret("SPRING_MAIL_FROM", smtpFrom);
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
