package com.demo.devops.auditservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.devops.auditservice.domain.AuditEvent;
import com.demo.devops.auditservice.dto.AuditRequest;
import com.demo.devops.auditservice.dto.StatusResponse;
import com.demo.devops.auditservice.repository.AuditEventRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuditControllerTest {
  private final AuditEventRepository repository = Mockito.mock(AuditEventRepository.class);
  private final AuditController controller = new AuditController(repository, "audit-key");

  @Test
  void createEventRejectsInvalidApiKeys() {
    RuntimeException error = assertThrows(
        RuntimeException.class,
        () ->
            controller.createEvent(
                "wrong-key",
                new AuditRequest("LOGIN_SUCCESS", "user@example.com", "ok", "auth-service")));
    assertNotNull(error);
  }

  @Test
  void createEventPersistsTheAuditRecord() {
    AtomicReference<AuditEvent> savedRef = new AtomicReference<>();
    when(repository.save(Mockito.any(AuditEvent.class)))
        .thenAnswer(
            invocation -> {
              AuditEvent event = invocation.getArgument(0, AuditEvent.class);
              savedRef.set(event);
              return event;
            });

    StatusResponse response =
        controller.createEvent(
            "audit-key",
            new AuditRequest("LOGIN_SUCCESS", "user@example.com", "ok", "auth-service"));

    assertEquals("ok", response.status());
    AuditEvent saved = savedRef.get();
    assertNotNull(saved);
    assertEquals("LOGIN_SUCCESS", saved.getEventType());
    assertEquals("user@example.com", saved.getActor());
    assertEquals("ok", saved.getDetails());
    assertEquals("auth-service", saved.getSource());
  }

  @Test
  void recentClampsTheRequestedLimit() {
    AuditEvent event = new AuditEvent();
    event.setEventType("MESSAGE_VIEW");
    when(repository.findRecent(100)).thenReturn(List.of(event));

    List<AuditEvent> events = controller.recent(500);

    assertEquals(1, events.size());
    assertEquals("MESSAGE_VIEW", events.get(0).getEventType());
    verify(repository).findRecent(100);
  }
}
