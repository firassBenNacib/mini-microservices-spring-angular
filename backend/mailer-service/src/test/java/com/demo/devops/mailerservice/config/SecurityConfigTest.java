package com.demo.devops.mailerservice.config;

import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.devops.mailerservice.service.MailerService;
import com.demo.devops.mailerservice.web.MailerController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MailerController.class, properties = "mailer.api-key=test-mailer-access-value")
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private MailerService mailerService;

  @Test
  void sendRequiresCsrfWhenApiKeyHeaderIsMissing() throws Exception {
    mockMvc.perform(post("/send")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"user@example.com","subject":"hello","text":"world"}
                """))
        .andExpect(status().isForbidden());

    then(mailerService).shouldHaveNoInteractions();
  }

  @Test
  void sendAcceptsCsrfTokenWithoutApiKeyHeader() throws Exception {
    mockMvc.perform(post("/send")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"user@example.com","subject":"hello","text":"world"}
                """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void sendRequiresCsrfEvenWhenApiKeyHeaderIsPresent() throws Exception {
    mockMvc.perform(post("/send")
            .header("x-mailer-key", "test-mailer-access-value")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"user@example.com","subject":"hello","text":"world"}
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void sendAllowsApiKeyRequestsWithCsrfToken() throws Exception {
    mockMvc.perform(post("/send")
            .with(csrf())
            .header("x-mailer-key", "test-mailer-access-value")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"to":"user@example.com","subject":"hello","text":"world"}
                """))
        .andExpect(status().isOk());
  }

  @Test
  void csrfEndpointProvidesToken() throws Exception {
    mockMvc.perform(get("/csrf"))
        .andExpect(status().isOk());
  }
}
