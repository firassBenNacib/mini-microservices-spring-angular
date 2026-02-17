package com.demo.devops.apiservice.web;

import com.demo.devops.apiservice.client.AuditClient;
import com.demo.devops.apiservice.client.MailerClient;
import com.demo.devops.apiservice.client.NotificationClient;
import com.demo.devops.apiservice.dto.MailRequest;
import com.demo.devops.apiservice.dto.MessageResponse;
import com.demo.devops.apiservice.dto.NotificationRequest;
import com.demo.devops.apiservice.dto.StatusResponse;
import jakarta.validation.Valid;
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
  private final MailerClient mailerClient;
  private final NotificationClient notificationClient;
  private final AuditClient auditClient;

  public ApiController(
      MailerClient mailerClient,
      NotificationClient notificationClient,
      AuditClient auditClient) {
    this.mailerClient = mailerClient;
    this.notificationClient = notificationClient;
    this.auditClient = auditClient;
  }

  @GetMapping("/health")
  public StatusResponse health() {
    return new StatusResponse("ok");
  }

  @GetMapping("/message")
  public MessageResponse message() {
    auditClient.sendEvent("MESSAGE_VIEW", currentActor(), "message viewed", "api-service");
    return new MessageResponse("Microservices deployed and working");
  }

  @PostMapping("/send-test-email")
  @ResponseStatus(HttpStatus.OK)
  public StatusResponse sendTestEmail(@Valid @RequestBody MailRequest request) {
    boolean sent = mailerClient.send(request);
    if (!sent) {
      auditClient.sendEvent("EMAIL_FAILED", currentActor(), "mailer error", "api-service");
      throw new MailerUnavailableException();
    }
    auditClient.sendEvent("EMAIL_SENT", currentActor(), "sent to " + request.to(), "api-service");
    return new StatusResponse("ok");
  }

  @PostMapping("/send-test-notification")
  @ResponseStatus(HttpStatus.OK)
  public StatusResponse sendTestNotification(@Valid @RequestBody NotificationRequest request) {
    boolean sent = notificationClient.send(request);
    if (!sent) {
      auditClient.sendEvent("NOTIFY_FAILED", currentActor(), "notification error", "api-service");
      throw new NotificationUnavailableException();
    }
    auditClient.sendEvent("NOTIFY_SENT", currentActor(), "sent to " + request.to(), "api-service");
    return new StatusResponse("ok");
  }

  private String currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getName() == null) {
      return "unknown";
    }
    return authentication.getName();
  }

  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  private static class MailerUnavailableException extends RuntimeException {}

  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  private static class NotificationUnavailableException extends RuntimeException {}
}
