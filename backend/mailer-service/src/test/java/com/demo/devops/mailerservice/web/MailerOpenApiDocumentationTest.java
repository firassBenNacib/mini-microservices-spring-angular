package com.demo.devops.mailerservice.web;

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
    "mailer.api-key=test-mailer-key-123",
    "spring.mail.host=smtp.test.local",
    "spring.mail.username=test-mail-user",
    "spring.mail.password=test-mail-password-123",
    "spring.mail.from=noreply@test.local"
})
@AutoConfigureMockMvc(addFilters = false)
class MailerOpenApiDocumentationTest {
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
    assertTrue(result.getOpenAPI().getPaths().containsKey("/send"));
  }
}
