package com.demo.devops.mailerservice.web;

import com.demo.devops.mailerservice.dto.MailRequest;
import com.demo.devops.mailerservice.dto.StatusResponse;
import com.demo.devops.mailerservice.service.MailerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class MailerController {
  private final MailerService mailerService;
  private final String apiKey;

  public MailerController(MailerService mailerService, @Value("${mailer.api-key}") String apiKey) {
    this.mailerService = mailerService;
    this.apiKey = apiKey;
  }

  @GetMapping("/health")
  public StatusResponse health() {
    return new StatusResponse("ok");
  }

  @PostMapping("/send")
  @ResponseStatus(HttpStatus.OK)
  public StatusResponse send(
      @RequestHeader(name = "x-mailer-key", required = false) String providedKey,
      @Valid @RequestBody MailRequest request) {
    if (providedKey == null || !providedKey.equals(apiKey)) {
      throw new InvalidKeyException();
    }

    mailerService.send(request.to(), request.subject(), request.text());
    return new StatusResponse("ok");
  }

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  private static class InvalidKeyException extends RuntimeException {}
}
