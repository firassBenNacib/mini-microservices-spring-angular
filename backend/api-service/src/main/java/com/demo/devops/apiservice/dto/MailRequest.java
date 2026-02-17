package com.demo.devops.apiservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MailRequest(
    @NotBlank @Email String to,
    @NotBlank String subject,
    @NotBlank String text
) {}
