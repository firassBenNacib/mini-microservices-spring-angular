package com.demo.devops.apiservice.client;

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
public class AuditClient {
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final Logger LOG = LoggerFactory.getLogger(AuditClient.class);

  private final RestTemplate restTemplate;
  private final String auditUrl;
  private final String auditCsrfUrl;
  private final String apiKey;

  public AuditClient(
      RestTemplateBuilder builder,
      @Value("${audit.url}") String auditUrl,
      @Value("${audit.api-key}") String apiKey,
      @Value("${audit.timeout-ms}") long timeoutMs) {
    this.auditUrl = Objects.requireNonNull(auditUrl, "audit.url must not be null");
    this.auditCsrfUrl = deriveUrl(this.auditUrl, "/audit/events", "/audit/csrf");
    this.apiKey = Objects.requireNonNull(apiKey, "audit.api-key must not be null");
    this.restTemplate = builder
        .requestFactory(() -> buildRequestFactory(timeoutMs))
        .build();
  }

  public void sendEvent(String eventType, String actor, String details, String source) {
    AuditEventRequest payload = new AuditEventRequest(eventType, actor, details, source);

    try {
      String csrfToken = fetchCsrfToken();
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("x-audit-key", Objects.requireNonNull(apiKey, "audit.api-key must not be null"));
      headers.set(CSRF_HEADER, csrfToken);
      headers.add(HttpHeaders.COOKIE, CSRF_COOKIE + "=" + csrfToken);
      HttpEntity<AuditEventRequest> entity = new HttpEntity<>(payload, headers);
      restTemplate.postForEntity(
          Objects.requireNonNull(auditUrl, "audit.url must not be null"),
          entity,
          String.class);
    } catch (RestClientException ex) {
      LOG.warn("audit_event_delivery_failed eventType={} source={} actor={} message={}", eventType, source, actor, ex.getMessage());
    }
  }

  private record AuditEventRequest(String eventType, String actor, String details, String source) {}
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
        restTemplate.exchange(auditCsrfUrl, HttpMethod.GET, HttpEntity.EMPTY, CsrfTokenResponse.class);
    CsrfTokenResponse body = response.getBody();
    if (body == null || body.token() == null || body.token().isBlank()) {
      throw new RestClientException("audit csrf token missing");
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
