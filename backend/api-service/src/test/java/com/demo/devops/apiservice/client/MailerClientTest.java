package com.demo.devops.apiservice.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.demo.devops.apiservice.dto.MailRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class MailerClientTest {

  @Test
  void sendFetchesCsrfTokenBeforePosting() {
    MailerClient client = new MailerClient(
        new RestTemplateBuilder(),
        "http://mailer.example/send",
        "test-mailer-access-value",
        1000);
    MockRestServiceServer server = MockRestServiceServer.bindTo(extractRestTemplate(client)).build();

    server.expect(requestTo("http://mailer.example/csrf"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"token\":\"csrf-token\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://mailer.example/send"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-mailer-key", "test-mailer-access-value"))
        .andExpect(header("X-XSRF-TOKEN", "csrf-token"))
        .andExpect(header(HttpHeaders.COOKIE, "XSRF-TOKEN=csrf-token"))
        .andRespond(withSuccess());

    boolean sent = client.send(new MailRequest("user@example.com", "hello", "world"));

    assertTrue(sent);
    server.verify();
  }

  @Test
  void sendReturnsFalseWhenCsrfTokenIsMissing() {
    MailerClient client = new MailerClient(
        new RestTemplateBuilder(),
        "http://mailer.example/send",
        "test-mailer-access-value",
        1000);
    MockRestServiceServer server = MockRestServiceServer.bindTo(extractRestTemplate(client)).build();

    server.expect(requestTo("http://mailer.example/csrf"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"token\":\"\"}", MediaType.APPLICATION_JSON));

    boolean sent = client.send(new MailRequest("user@example.com", "hello", "world"));

    assertFalse(sent);
    server.verify();
  }

  private static RestTemplate extractRestTemplate(MailerClient client) {
    return (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
  }
}
