package com.demo.devops.auditservice.web;

import com.demo.devops.auditservice.domain.AuditEvent;
import com.demo.devops.auditservice.dto.AuditRequest;
import com.demo.devops.auditservice.dto.StatusResponse;
import com.demo.devops.auditservice.repository.AuditEventRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
public class AuditController {
  private final AuditEventRepository repository;
  private final String apiKey;

  public AuditController(
      AuditEventRepository repository,
      @Value("${audit.api-key}") String apiKey) {
    this.repository = repository;
    this.apiKey = apiKey;
  }

  @GetMapping("/health")
  public StatusResponse health() {
    return new StatusResponse("ok");
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.OK)
  public StatusResponse createEvent(
      @RequestHeader(name = "x-audit-key", required = false) String providedKey,
      @Valid @RequestBody AuditRequest request) {
    if (providedKey == null || !providedKey.equals(apiKey)) {
      throw new InvalidKeyException();
    }
    AuditEvent event = new AuditEvent();
    event.setEventType(request.eventType());
    event.setActor(request.actor());
    event.setDetails(request.details());
    event.setSource(request.source());
    repository.save(event);
    return new StatusResponse("ok");
  }

  @GetMapping("/recent")
  public List<AuditEvent> recent(@RequestParam(name = "limit", defaultValue = "20") int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 100);
    return repository.findRecent(safeLimit);
  }

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  private static class InvalidKeyException extends RuntimeException {}

}
