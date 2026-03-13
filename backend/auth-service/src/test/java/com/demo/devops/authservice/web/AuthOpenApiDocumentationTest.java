package com.demo.devops.authservice.web;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.jwt.current-secret=01234567890123456789012345678901",
    "app.demo-user.password=ValidDemoPassword123!",
    "spring.datasource.url=jdbc:h2:mem:authdocs;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "audit.url=http://localhost:8084/audit/events",
    "logging.level.org.springdoc.core.events.SpringDocAppInitializer=ERROR"
})
@AutoConfigureMockMvc(addFilters = false)
class AuthOpenApiDocumentationTest {
  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.password", () -> "docs-auth-db-access");
    registry.add("audit.api-key", () -> "docs-audit-access-value");
  }

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
    assertTrue(result.getOpenAPI().getPaths().containsKey("/auth/login"));
    assertTrue(result.getOpenAPI().getPaths().containsKey("/auth/session"));
    assertTrue(result.getOpenAPI().getPaths().containsKey("/auth/refresh"));
    assertTrue(result.getOpenAPI().getPaths().containsKey("/auth/logout"));
  }
}
