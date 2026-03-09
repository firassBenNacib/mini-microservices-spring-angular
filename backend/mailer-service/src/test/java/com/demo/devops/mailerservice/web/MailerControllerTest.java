package com.demo.devops.mailerservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.demo.devops.mailerservice.dto.MailRequest;
import com.demo.devops.mailerservice.dto.StatusResponse;
import com.demo.devops.mailerservice.service.MailerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MailerControllerTest {
  private MailerService mailerService;
  private MailerController controller;

  @BeforeEach
  void setUp() {
    mailerService = Mockito.mock(MailerService.class);
    controller = new MailerController(mailerService, "mailer-key");
  }

  @Test
  void sendRejectsInvalidApiKeys() {
    assertThrows(
        RuntimeException.class,
        () -> controller.send("wrong-key", new MailRequest("user@example.com", "hello", "world")));
  }

  @Test
  void sendDelegatesToMailerServiceWhenAuthorized() {
    StatusResponse response =
        controller.send("mailer-key", new MailRequest("user@example.com", "hello", "world"));

    assertEquals("ok", response.status());
    verify(mailerService).send("user@example.com", "hello", "world");
  }
}
