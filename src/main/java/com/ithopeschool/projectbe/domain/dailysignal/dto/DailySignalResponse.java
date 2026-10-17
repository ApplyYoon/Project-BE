package com.ithopeschool.projectbe.domain.dailysignal.dto;

import com.ithopeschool.projectbe.domain.dailysignal.DailySignal;
import java.time.LocalDate;

// 하루 신호 하나를 FE 에 알려 주는 모양입니다.
//   { "id": "5", "date": "2026-10-17", "sleepMinutes": 420, "sleptAt": 1792161000000,
//     "wokeAt": 1792188000000, "steps": 8500, "snsOverThirtyMinutes": true }
// - id 는 다른 API 와 같이 글자로 보냅니다.
// - 시각은 밀리초 숫자입니다. (docs/API.md 2-2)
// - 모르는 값은 null 입니다. (0 이 아닙니다)
// - 누구의 데이터인지(userId)는 담지 않습니다.
public record DailySignalResponse(
    String id,
    LocalDate date,
    Integer sleepMinutes,
    Long sleptAt,
    Long wokeAt,
    Integer steps,
    Boolean snsOverThirtyMinutes) {

  public static DailySignalResponse from(DailySignal signal) {
    return new DailySignalResponse(
        String.valueOf(signal.getId()),
        signal.getSignalDate(),
        signal.getSleepMinutes(),
        signal.getSleptAt() == null ? null : signal.getSleptAt().toEpochMilli(),
        signal.getWokeAt() == null ? null : signal.getWokeAt().toEpochMilli(),
        signal.getSteps(),
        signal.getSnsOverThirtyMinutes());
  }
}
