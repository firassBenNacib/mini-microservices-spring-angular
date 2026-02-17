package com.demo.devops.apiservice.client;

import com.demo.devops.apiservice.dto.MailRequest;
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
public class MailerClient {
  private final RestTemplate restTemplate;
  private final String mailerUrl;
  private final String apiKey;

  public MailerClient(
      RestTemplateBuilder builder,
      @Value("${mailer.url}") String mailerUrl,
      @Value("${mailer.api-key}") String apiKey,
      @Value("${mailer.timeout-ms}") long timeoutMs) {
    this.mailerUrl = mailerUrl;
    this.apiKey = apiKey;
    this.restTemplate = builder
        .requestFactory(() -> buildRequestFactory(timeoutMs))
        .build();
  }

  public boolean send(MailRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("x-mailer-key", apiKey);

    HttpEntity<MailRequest> entity = new HttpEntity<>(request, headers);
    try {
      restTemplate.postForEntity(mailerUrl, entity, String.class);
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
