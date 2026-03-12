package com.demo.devops.auditservice.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.devops.auditservice.domain.AuditEvent;
import com.demo.devops.auditservice.repository.AuditEventRepository;
import com.demo.devops.auditservice.security.JwtAuthFilter;
import com.demo.devops.auditservice.security.JwtService;
import com.demo.devops.auditservice.web.AuditController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuditController.class, properties = {
    "audit.api-key=test-audit-access-value",
    "app.jwt.current-secret=01234567890123456789012345678901"
})
@Import({SecurityConfig.class, JwtAuthFilter.class})
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AuditEventRepository repository;

  @MockBean
  private JwtService jwtService;

  @Test
  void auditEventRequiresCsrfWhenApiKeyHeaderIsMissing() throws Exception {
    mockMvc.perform(post("/audit/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"eventType":"LOGIN_SUCCESS","actor":"user@example.com","details":"ok","source":"auth-service"}
                """))
        .andExpect(status().isForbidden());

    then(repository).shouldHaveNoInteractions();
  }

  @Test
  void auditEventAcceptsCsrfTokenWithoutApiKeyHeader() throws Exception {
    mockMvc.perform(post("/audit/events")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"eventType":"LOGIN_SUCCESS","actor":"user@example.com","details":"ok","source":"auth-service"}
                """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void auditEventAllowsApiKeyRequestsWithoutCsrfToken() throws Exception {
    mockMvc.perform(post("/audit/events")
            .header("x-audit-key", "test-audit-access-value")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"eventType":"LOGIN_SUCCESS","actor":"user@example.com","details":"ok","source":"auth-service"}
                """))
        .andExpect(status().isOk());
  }

  @Test
  void recentEndpointStillRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/audit/recent"))
        .andExpect(status().isForbidden());
  }

  @Test
  void recentEndpointAcceptsAuthenticatedRequests() throws Exception {
    given(repository.findRecent(20)).willReturn(List.of(new AuditEvent()));

    mockMvc.perform(get("/audit/recent").with(user("demo@example.com")))
        .andExpect(status().isOk());
  }
}
