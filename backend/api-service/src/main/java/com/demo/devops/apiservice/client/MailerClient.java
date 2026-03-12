package com.demo.devops.apiservice.client;

import com.demo.devops.apiservice.dto.MailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.util.Objects;

@Component
public class MailerClient {
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final Logger LOG = LoggerFactory.getLogger(MailerClient.class);

  private final RestTemplate restTemplate;
  private final String mailerUrl;
  private final String mailerCsrfUrl;
  private final String apiKey;

  public MailerClient(
      RestTemplateBuilder builder,
      @Value("${mailer.url}") String mailerUrl,
      @Value("${mailer.api-key}") String apiKey,
      @Value("${mailer.timeout-ms}") long timeoutMs) {
    this.mailerUrl = Objects.requireNonNull(mailerUrl, "mailer.url must not be null");
    this.mailerCsrfUrl = deriveUrl(this.mailerUrl, "/send", "/csrf");
    this.apiKey = Objects.requireNonNull(apiKey, "mailer.api-key must not be null");
    this.restTemplate = builder
        .requestFactory(() -> buildRequestFactory(timeoutMs))
        .build();
  }

  public boolean send(MailRequest request) {
    try {
      String csrfToken = fetchCsrfToken();
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("x-mailer-key", Objects.requireNonNull(apiKey, "mailer.api-key must not be null"));
      headers.set(CSRF_HEADER, csrfToken);
      headers.add(HttpHeaders.COOKIE, CSRF_COOKIE + "=" + csrfToken);
      HttpEntity<MailRequest> entity = new HttpEntity<>(request, headers);
      restTemplate.postForEntity(
          Objects.requireNonNull(mailerUrl, "mailer.url must not be null"),
          entity,
          String.class);
      return true;
    } catch (RestClientException ex) {
      LOG.warn("mailer_request_failed url={} message={}", mailerUrl, ex.getMessage());
      return false;
    }
  }

  private record CsrfTokenResponse(String token) {}

  private static SimpleClientHttpRequestFactory buildRequestFactory(long timeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    int timeout = Math.toIntExact(timeoutMs);
    factory.setConnectTimeout(timeout);
    factory.setReadTimeout(timeout);
    return factory;
  }

  private String fetchCsrfToken() {
    ResponseEntity<CsrfTokenResponse> response =
        restTemplate.exchange(mailerCsrfUrl, HttpMethod.GET, HttpEntity.EMPTY, CsrfTokenResponse.class);
    CsrfTokenResponse body = response.getBody();
    if (body == null || body.token() == null || body.token().isBlank()) {
      throw new RestClientException("mailer csrf token missing");
    }
    return body.token();
  }

  private static String deriveUrl(String baseUrl, String suffix, String replacement) {
    String normalized = baseUrl.trim();
    return normalized.endsWith(suffix)
        ? normalized.substring(0, normalized.length() - suffix.length()) + replacement
        : normalized + replacement;
  }
}
