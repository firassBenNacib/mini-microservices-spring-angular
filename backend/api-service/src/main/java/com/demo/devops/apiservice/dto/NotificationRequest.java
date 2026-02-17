package com.demo.devops.apiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NotificationRequest(
    @NotBlank
    @Pattern(
        regexp = "^\\+[1-9]\\d{7,14}$",
        message = "to must be a valid E.164 phone number, for example +12025550123")
    String to,
    @NotBlank String subject,
    @NotBlank String text
) {}
