package com.demo.devops.apiservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.jwt.current-secret=01234567890123456789012345678901",
    "mailer.api-key=test-mailer-key-123",
    "notify.api-key=test-notify-key-123",
    "audit.api-key=test-audit-key-123",
    "mailer.url=http://localhost:8083/send",
    "notify.url=http://localhost:8090/notify",
    "audit.url=http://localhost:8084/audit/events"
})
@AutoConfigureMockMvc(addFilters = false)
class ApiOpenApiDocumentationTest {
  @Autowired
  private MockMvc mockMvc;

  @Test
  void apiDocsAreServedAsValidOpenApi() throws Exception {
    String payload = mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    SwaggerParseResult result = new OpenAPIParser().readContents(payload, null, null);

    assertNotNull(result.getOpenAPI());
    assertTrue(result.getMessages().isEmpty(), () -> "Unexpected OpenAPI parser messages: " + result.getMessages());
    assertEquals("3.1.0", result.getOpenAPI().getOpenapi());
    assertTrue(result.getOpenAPI().getPaths().containsKey("/api/message"));
    assertTrue(result.getOpenAPI().getPaths().containsKey("/api/send-test-email"));
    assertTrue(result.getOpenAPI().getPaths().containsKey("/api/send-test-notification"));
  }
}
