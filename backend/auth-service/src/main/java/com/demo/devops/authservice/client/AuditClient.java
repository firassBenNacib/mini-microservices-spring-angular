package com.demo.devops.authservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.util.Objects;

@Component
public class AuditClient {
  private static final Logger LOG = LoggerFactory.getLogger(AuditClient.class);

  private final RestTemplate restTemplate;
  private final String auditUrl;
  private final String apiKey;

  public AuditClient(
      RestTemplateBuilder builder,
      @Value("${audit.url}") String auditUrl,
      @Value("${audit.api-key}") String apiKey,
      @Value("${audit.timeout-ms}") long timeoutMs) {
    this.auditUrl = Objects.requireNonNull(auditUrl, "audit.url must not be null");
    this.apiKey = Objects.requireNonNull(apiKey, "audit.api-key must not be null");
    this.restTemplate = builder
        .requestFactory(() -> buildRequestFactory(timeoutMs))
        .build();
  }

  public void sendEvent(String eventType, String actor, String details, String source) {
    AuditEventRequest payload = new AuditEventRequest(eventType, actor, details, source);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("x-audit-key", Objects.requireNonNull(apiKey, "audit.api-key must not be null"));
    HttpEntity<AuditEventRequest> entity = new HttpEntity<>(payload, headers);

    try {
      restTemplate.postForEntity(
          Objects.requireNonNull(auditUrl, "audit.url must not be null"),
          entity,
          String.class);
    } catch (RestClientException ex) {
      LOG.warn("audit_event_delivery_failed eventType={} source={} actor={} message={}", eventType, source, actor, ex.getMessage());
    }
  }

  private record AuditEventRequest(String eventType, String actor, String details, String source) {}

  private static SimpleClientHttpRequestFactory buildRequestFactory(long timeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    int timeout = Math.toIntExact(timeoutMs);
    factory.setConnectTimeout(timeout);
    factory.setReadTimeout(timeout);
    return factory;
  }
}
