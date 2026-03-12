package com.demo.devops.authservice.client;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AuditClientTest {

  @Test
  void sendEventFetchesCsrfTokenBeforePosting() {
    AuditClient client = new AuditClient(
        new RestTemplateBuilder(),
        "http://audit.example/audit/events",
        "test-audit-access-value",
        1000);
    MockRestServiceServer server = MockRestServiceServer.bindTo(extractRestTemplate(client)).build();

    server.expect(requestTo("http://audit.example/audit/csrf"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"token\":\"csrf-token\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://audit.example/audit/events"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-audit-key", "test-audit-access-value"))
        .andExpect(header("X-XSRF-TOKEN", "csrf-token"))
        .andExpect(header(HttpHeaders.COOKIE, "XSRF-TOKEN=csrf-token"))
        .andRespond(withSuccess());

    client.sendEvent("LOGIN_SUCCESS", "user@example.com", "login successful", "auth-service");

    server.verify();
  }

  @Test
  void sendEventStopsWhenCsrfTokenIsMissing() {
    AuditClient client = new AuditClient(
        new RestTemplateBuilder(),
        "http://audit.example/audit/events",
        "test-audit-access-value",
        1000);
    MockRestServiceServer server = MockRestServiceServer.bindTo(extractRestTemplate(client)).build();

    server.expect(requestTo("http://audit.example/audit/csrf"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"token\":\"\"}", MediaType.APPLICATION_JSON));

    client.sendEvent("LOGIN_FAILURE", "user@example.com", "csrf missing", "auth-service");

    server.verify();
  }

  private static RestTemplate extractRestTemplate(AuditClient client) {
    return (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
  }
}
