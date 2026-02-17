package com.demo.devops.authservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AuditClient {
  private final RestTemplate restTemplate;
  private final String auditUrl;
  private final String apiKey;

  public AuditClient(
      RestTemplateBuilder builder,
      @Value("${audit.url}") String auditUrl,
      @Value("${audit.api-key}") String apiKey,
      @Value("${audit.timeout-ms}") long timeoutMs) {
    this.auditUrl = auditUrl;
    this.apiKey = apiKey;
    this.restTemplate = builder
        .requestFactory(() -> buildRequestFactory(timeoutMs))
        .build();
  }

  public void sendEvent(String eventType, String actor, String details, String source) {
    AuditEventRequest payload = new AuditEventRequest(eventType, actor, details, source);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("x-audit-key", apiKey);
    HttpEntity<AuditEventRequest> entity = new HttpEntity<>(payload, headers);

    try {
      restTemplate.postForEntity(auditUrl, entity, String.class);
    } catch (RestClientException ignored) {}
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
