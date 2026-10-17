package com.ithopeschool.projectbe.domain.focus.dto;

import com.ithopeschool.projectbe.domain.focus.LockEvent;
import java.time.Instant;

public record LockEventResponse(Long id, Instant occurredAt, String appName, String reason) {
  public static LockEventResponse of(LockEvent e) {
    return new LockEventResponse(e.getId(), e.getOccurredAt(), e.getAppName(), e.getReason());
  }
}
