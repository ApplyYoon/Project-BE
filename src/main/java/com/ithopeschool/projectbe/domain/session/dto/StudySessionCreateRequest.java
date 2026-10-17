package com.ithopeschool.projectbe.domain.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// "공부 기록 저장" 요청의 모양입니다. (FE 의 StudySession 에서 id 를 뺀 것)
//   { "subjectId": "3", "startedAt": 1790000000000, "endedAt": 1790003000000,
//     "pauseCount": 2, "pausedMs": 300000, "planId": "7", "targetMinutes": 50,
//     "stopReason": "phone", "kind": "study" }
// - 시각은 밀리초 숫자입니다. (docs/API.md 2-2)
// - pausedMs, planId, targetMinutes, stopReason, kind 는 보내지 않아도 됩니다.
// - 노는 기록("kind": "play")은 subjectId 를 보내지 않거나 빈 글자("")로 보냅니다.
// - 시각끼리 맞는지, 내 과목·내 계획인지, kind·stopReason 이 정해진 값인지는 Service 에서 검사합니다.
public record StudySessionCreateRequest(
    // FE 의 id 는 글자라서 글자로 받습니다. 숫자만 받습니다. ("3" 은 되고 "abc" 는 안 됨)
    // 18자리까지만 받습니다. 그보다 길면 서버의 숫자(Long)에 안 들어가서 500 이 나기 때문입니다.
    @Pattern(regexp = "\\d{0,18}", message = "과목 번호가 올바르지 않아요") String subjectId,
    @NotNull(message = "시작 시각을 보내 주세요") Long startedAt,
    @NotNull(message = "끝 시각을 보내 주세요") Long endedAt,
    @NotNull(message = "일시정지 횟수를 보내 주세요") @Min(value = 0, message = "일시정지 횟수는 0 이상이어야 해요")
        Integer pauseCount,
    // 안 보내면 0 으로 봅니다. (end − start) 보다 작은지는 Service 에서 봅니다.
    @Min(value = 0, message = "일시정지 시간은 0 이상이어야 해요") Long pausedMs,
    @Pattern(regexp = "\\d{0,18}", message = "계획 번호가 올바르지 않아요") String planId,
    // 계획의 목표 시간과 같은 범위입니다.
    @Min(value = 1, message = "목표 시간은 1분 이상이어야 해요")
        @Max(value = 600, message = "목표 시간은 600분(10시간) 이하여야 해요")
        Integer targetMinutes,
    String stopReason,
    String kind) {}
