package com.demo.devops.apiservice.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.devops.apiservice.client.AuditClient;
import com.demo.devops.apiservice.client.MailerClient;
import com.demo.devops.apiservice.client.NotificationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.jwt.current-secret=01234567890123456789012345678901",
    "mailer.url=http://localhost:8083/send",
    "notify.url=http://localhost:8090/notify",
    "audit.url=http://localhost:8084/audit/events"
})
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private MailerClient mailerClient;

  @MockBean
  private NotificationClient notificationClient;

  @MockBean
  private AuditClient auditClient;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("mailer.api-key", () -> "test-mailer-access-value");
    registry.add("notify.api-key", () -> "test-notify-access-value");
    registry.add("audit.api-key", () -> "test-audit-access-value");
  }

  @Test
  void sendTestEmailAllowsAuthenticatedRequestsWithoutCsrfToken() throws Exception {
    given(mailerClient.send(any())).willReturn(true);

    mockMvc.perform(post("/api/send-test-email")
            .with(user("demo@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"user@example.com","subject":"hello","text":"world"}
                """))
        .andExpect(status().isOk());
  }

  @Test
  void sendTestEmailAcceptsValidCsrfToken() throws Exception {
    given(mailerClient.send(any())).willReturn(true);

    mockMvc.perform(post("/api/send-test-email")
            .with(user("demo@example.com"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"user@example.com","subject":"hello","text":"world"}
                """))
        .andExpect(status().isOk());
  }

  @Test
  void sendTestNotificationAllowsAuthenticatedRequestsWithoutCsrfToken() throws Exception {
    given(notificationClient.send(any())).willReturn(true);

    mockMvc.perform(post("/api/send-test-notification")
            .with(user("demo@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"+12025550123","subject":"hello","text":"world"}
                """))
        .andExpect(status().isOk());
  }

  @Test
  void sendTestNotificationAcceptsValidCsrfToken() throws Exception {
    given(notificationClient.send(any())).willReturn(true);

    mockMvc.perform(post("/api/send-test-notification")
            .with(user("demo@example.com"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"+12025550123","subject":"hello","text":"world"}
                """))
        .andExpect(status().isOk());
  }

  @Test
  void sendTestNotificationReturnsBadGatewayWhenNotificationClientFails() throws Exception {
    given(notificationClient.send(any())).willReturn(false);

    mockMvc.perform(post("/api/send-test-notification")
            .with(user("demo@example.com"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"+12025550123","subject":"hello","text":"world"}
                """))
        .andExpect(status().isBadGateway());
  }

  @Test
  void sendTestNotificationReturnsBadGatewayWithoutCsrfTokenWhenNotificationClientFails()
      throws Exception {
    given(notificationClient.send(any())).willReturn(false);

    mockMvc.perform(post("/api/send-test-notification")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"+12025550123","subject":"hello","text":"world"}
                """))
        .andExpect(status().isBadGateway());
  }

  @Test
  void secondProtectedPostSucceedsWhenEachRequestProvidesACsrfToken() throws Exception {
    given(mailerClient.send(any())).willReturn(true);
    given(notificationClient.send(any())).willReturn(true);

    mockMvc.perform(post("/api/send-test-email")
            .with(user("demo@example.com"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"user@example.com","subject":"hello","text":"world"}
                """))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/send-test-notification")
            .with(user("demo@example.com"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"+12025550123","subject":"hello","text":"world"}
                """))
        .andExpect(status().isOk());
  }
}
