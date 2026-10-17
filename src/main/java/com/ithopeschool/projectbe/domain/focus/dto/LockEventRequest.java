package com.ithopeschool.projectbe.domain.focus.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record LockEventRequest(
    @NotNull Instant occurredAt,
    @NotBlank @Size(max = 30) String appName,
    @NotBlank @Size(max = 50) String reason) {}
