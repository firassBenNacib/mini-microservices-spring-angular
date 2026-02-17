package com.demo.devops.apiservice.client;

import com.demo.devops.apiservice.dto.NotificationRequest;
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
public class NotificationClient {
  private final RestTemplate restTemplate;
  private final String notifyUrl;
  private final String apiKey;

  public NotificationClient(
      RestTemplateBuilder builder,
      @Value("${notify.url}") String notifyUrl,
      @Value("${notify.api-key}") String apiKey,
      @Value("${notify.timeout-ms}") long timeoutMs) {
    this.notifyUrl = notifyUrl;
    this.apiKey = apiKey;
    this.restTemplate = builder
        .requestFactory(() -> buildRequestFactory(timeoutMs))
        .build();
  }

  public boolean send(NotificationRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("x-notify-key", apiKey);

    HttpEntity<NotificationRequest> entity = new HttpEntity<>(request, headers);
    try {
      restTemplate.postForEntity(notifyUrl, entity, String.class);
      return true;
    } catch (RestClientException ex) {
      return false;
    }
  }

  private static SimpleClientHttpRequestFactory buildRequestFactory(long timeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    int timeout = Math.toIntExact(timeoutMs);
    factory.setConnectTimeout(timeout);
    factory.setReadTimeout(timeout);
    return factory;
  }
}
