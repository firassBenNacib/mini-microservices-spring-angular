package com.demo.devops.auditservice.dto;

import jakarta.validation.constraints.NotBlank;

public record AuditRequest(
    @NotBlank String eventType,
    String actor,
    String details,
    String source
) {}
