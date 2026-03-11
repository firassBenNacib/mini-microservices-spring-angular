package com.demo.devops.apiservice.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.demo.devops.apiservice.client.AuditClient;
import com.demo.devops.apiservice.client.MailerClient;
import com.demo.devops.apiservice.client.NotificationClient;
import com.demo.devops.apiservice.dto.MailRequest;
import com.demo.devops.apiservice.dto.MessageResponse;
import com.demo.devops.apiservice.dto.NotificationRequest;
import com.demo.devops.apiservice.dto.StatusResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {
  private static final String API_SERVICE = "api-service";
  private static final String UNKNOWN = "unknown";

  private final MailerClient mailerClient;
  private final NotificationClient notificationClient;
  private final AuditClient auditClient;
  private final HttpClient healthClient;
  private final ObjectMapper objectMapper;
  private final URI authHealthUri;
  private final URI auditHealthUri;
  private final URI mailerHealthUri;
  private final URI notifyHealthUri;

  public ApiController(
      MailerClient mailerClient,
      NotificationClient notificationClient,
      AuditClient auditClient,
      @Value("${APP_AUTH_HEALTH_URL:http://auth-service:8081/auth/health}") String authHealthUrl,
      @Value("${AUDIT_URL:http://audit-service:8084/audit/events}") String auditUrl,
      @Value("${MAILER_URL:http://mailer-service:8083/send}") String mailerUrl,
      @Value("${NOTIFY_URL:http://notification-service:8090/notify}") String notifyUrl) {
    this.mailerClient = mailerClient;
    this.notificationClient = notificationClient;
    this.auditClient = auditClient;
    this.healthClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    this.objectMapper = new ObjectMapper();
    this.authHealthUri = URI.create(authHealthUrl);
    this.auditHealthUri = URI.create(deriveHealthUrl(auditUrl, "/audit/events", "/audit/health"));
    this.mailerHealthUri = URI.create(deriveHealthUrl(mailerUrl, "/send", "/health"));
    this.notifyHealthUri = URI.create(deriveHealthUrl(notifyUrl, "/notify", "/health"));
  }

  @GetMapping("/health")
  public StatusResponse health() {
    return new StatusResponse("ok");
  }

  @GetMapping("/message")
  public MessageResponse message() {
    auditClient.sendEvent("MESSAGE_VIEW", currentActor(), "message viewed", API_SERVICE);
    return new MessageResponse("Microservices deployed and working");
  }

  @GetMapping("/dashboard-status")
  public DashboardStatusResponse dashboardStatus() {
    return new DashboardStatusResponse(List.of(
        new ServiceStatus("gateway", "Gateway", "up", ""),
        checkService("auth", "Auth Service", authHealthUri),
        new ServiceStatus("api", "API Service", "up", ""),
        checkService("audit", "Audit Service", auditHealthUri),
        checkService("mailer", "Mailer Service", mailerHealthUri),
        checkService("notify", "Notification Service", notifyHealthUri)));
  }

  @PostMapping("/send-test-email")
  @ResponseStatus(HttpStatus.OK)
  public StatusResponse sendTestEmail(@Valid @RequestBody MailRequest request) {
    boolean sent = mailerClient.send(request);
    if (!sent) {
      auditClient.sendEvent("EMAIL_FAILED", currentActor(), "mailer error", API_SERVICE);
      throw new MailerUnavailableException();
    }
    auditClient.sendEvent("EMAIL_SENT", currentActor(), "sent to " + request.to(), API_SERVICE);
    return new StatusResponse("ok");
  }

  @PostMapping("/send-test-notification")
  @ResponseStatus(HttpStatus.OK)
  public StatusResponse sendTestNotification(@Valid @RequestBody NotificationRequest request) {
    boolean sent = notificationClient.send(request);
    if (!sent) {
      auditClient.sendEvent("NOTIFY_FAILED", currentActor(), "notification error", API_SERVICE);
      throw new NotificationUnavailableException();
    }
    auditClient.sendEvent("NOTIFY_SENT", currentActor(), "sent to " + request.to(), API_SERVICE);
    return new StatusResponse("ok");
  }

  private String currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getName() == null) {
      return UNKNOWN;
    }
    return authentication.getName();
  }

  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  private static class MailerUnavailableException extends RuntimeException {}

  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  private static class NotificationUnavailableException extends RuntimeException {}

  private ServiceStatus checkService(String key, String label, URI uri) {
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(3))
        .header("Accept", "application/json")
        .GET()
        .build();
    try {
      HttpResponse<String> response = healthClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return new ServiceStatus(key, label, "down", "HTTP " + response.statusCode());
      }

      String detail = extractStatus(response.body());
      return new ServiceStatus(key, label, "up", "ok".equalsIgnoreCase(detail) ? "" : detail);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return new ServiceStatus(key, label, UNKNOWN, "unreachable or timeout");
    } catch (IOException ex) {
      return new ServiceStatus(key, label, UNKNOWN, "unreachable or timeout");
    }
  }

  private String deriveHealthUrl(String baseUrl, String suffix, String replacement) {
    String normalized = baseUrl.trim();
    return normalized.endsWith(suffix)
        ? normalized.substring(0, normalized.length() - suffix.length()) + replacement
        : normalized + replacement;
  }

  private String extractStatus(String body) {
    try {
      JsonNode node = objectMapper.readTree(body);
      JsonNode status = node.get("status");
      if (status != null && status.isTextual() && !status.asText().isBlank()) {
        return status.asText();
      }
    } catch (IOException ignored) {
      // Fall back to a generic healthy state when upstream responds with non-JSON.
    }
    return "ok";
  }

  public record DashboardStatusResponse(List<ServiceStatus> services) {}

  public record ServiceStatus(String key, String label, String state, String detail) {}
}
