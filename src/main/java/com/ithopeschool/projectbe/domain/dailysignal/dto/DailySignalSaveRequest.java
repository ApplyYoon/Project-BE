package com.ithopeschool.projectbe.domain.dailysignal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// "하루 신호 보내기" 요청의 모양입니다.
//   { "date": "2026-10-17", "sleepMinutes": 420, "sleptAt": 1792161000000,
//     "wokeAt": 1792188000000, "steps": 8500, "snsOverThirtyMinutes": true }
// - date 만 꼭 있어야 하고, 나머지는 모두 보내지 않아도(null) 됩니다.
// - 시각은 밀리초 숫자입니다. (docs/API.md 2-2)
// - 일어난 시각이 잠든 시각보다 뒤인지, 오지 않은 날짜인지는 Service 에서 검사합니다.
public record DailySignalSaveRequest(
    @NotNull(message = "날짜를 보내 주세요") LocalDate date,
    // 하루는 1440분이라 그보다 길게 잘 수는 없습니다.
    @Min(value = 0, message = "수면 시간은 0분 이상이어야 해요")
        @Max(value = 1440, message = "수면 시간은 1440분(24시간) 이하여야 해요")
        Integer sleepMinutes,
    Long sleptAt,
    Long wokeAt,
    // 하루 10만 걸음이 넘으면 기기 오류로 봅니다.
    @Min(value = 0, message = "걸음 수는 0 이상이어야 해요")
        @Max(value = 100000, message = "걸음 수는 100000 이하여야 해요")
        Integer steps,
    Boolean snsOverThirtyMinutes) {}
