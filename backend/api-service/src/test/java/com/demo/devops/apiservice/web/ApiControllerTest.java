package com.demo.devops.apiservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.devops.apiservice.client.AuditClient;
import com.demo.devops.apiservice.client.MailerClient;
import com.demo.devops.apiservice.client.NotificationClient;
import com.demo.devops.apiservice.dto.MailRequest;
import com.demo.devops.apiservice.dto.MessageResponse;
import com.demo.devops.apiservice.dto.NotificationRequest;
import com.demo.devops.apiservice.dto.StatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiControllerTest {
  private MailerClient mailerClient;
  private NotificationClient notificationClient;
  private AuditClient auditClient;
  private ApiController controller;

  @BeforeEach
  void setUp() {
    mailerClient = Mockito.mock(MailerClient.class);
    notificationClient = Mockito.mock(NotificationClient.class);
    auditClient = Mockito.mock(AuditClient.class);
    controller =
        new ApiController(
            mailerClient,
            notificationClient,
            auditClient,
            "http://localhost:18081/auth/health",
            "http://localhost:18084/audit/events",
            "http://localhost:18083/send",
            "http://localhost:18090/notify");
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user@example.com", "n/a"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void messageReturnsPayloadAndAuditsViewEvent() {
    MessageResponse response = controller.message();

    assertEquals("Microservices deployed and working", response.message());
    verify(auditClient)
        .sendEvent("MESSAGE_VIEW", "user@example.com", "message viewed", "api-service");
  }

  @Test
  void messageUsesUnknownActorWhenAuthenticationIsMissing() {
    SecurityContextHolder.clearContext();

    controller.message();

    verify(auditClient).sendEvent("MESSAGE_VIEW", "unknown", "message viewed", "api-service");
  }

  @Test
  void sendTestEmailReturnsOkWhenMailerSucceeds() {
    when(mailerClient.send(new MailRequest("user@example.com", "hello", "world"))).thenReturn(true);

    StatusResponse response =
        controller.sendTestEmail(new MailRequest("user@example.com", "hello", "world"));

    assertEquals("ok", response.status());
    verify(auditClient)
        .sendEvent("EMAIL_SENT", "user@example.com", "sent to user@example.com", "api-service");
  }

  @Test
  void sendTestEmailThrowsWhenMailerFails() {
    when(mailerClient.send(new MailRequest("user@example.com", "hello", "world"))).thenReturn(false);

    assertThrows(
        RuntimeException.class,
        () -> controller.sendTestEmail(new MailRequest("user@example.com", "hello", "world")));

    verify(auditClient)
        .sendEvent("EMAIL_FAILED", "user@example.com", "mailer error", "api-service");
  }

  @Test
  void sendTestNotificationThrowsWhenNotificationClientFails() {
    when(notificationClient.send(new NotificationRequest("+12025550123", "hello", "world")))
        .thenReturn(false);

    assertThrows(
        RuntimeException.class,
        () -> controller.sendTestNotification(new NotificationRequest("+12025550123", "hello", "world")));

    verify(auditClient)
        .sendEvent(
            "NOTIFY_FAILED",
            "user@example.com",
            "notification error",
            "api-service");
  }

  @Test
  void sendTestNotificationReturnsOkWhenNotificationClientSucceeds() {
    when(notificationClient.send(new NotificationRequest("+12025550123", "hello", "world")))
        .thenReturn(true);

    StatusResponse response =
        controller.sendTestNotification(new NotificationRequest("+12025550123", "hello", "world"));

    assertEquals("ok", response.status());
    verify(auditClient)
        .sendEvent("NOTIFY_SENT", "user@example.com", "sent to +12025550123", "api-service");
  }
}
