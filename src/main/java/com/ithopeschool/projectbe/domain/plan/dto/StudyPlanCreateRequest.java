package com.ithopeschool.projectbe.domain.plan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// "계획 추가" 요청의 모양입니다. (FE 의 StudyPlan 에서 id·done·routineId 를 뺀 것)
//   { "date": "2026-10-05", "subjectId": "3", "content": "수학 문제집 3단원 풀기",
//     "targetMinutes": 90, "startTime": "19:00", "kind": "study" }
// - startTime, kind 는 보내지 않아도 됩니다. (시간 미정 · 공부 계획)
// - 노는 계획("kind": "play")은 subjectId 를 보내지 않거나 빈 글자("")로 보냅니다.
// - subjectId 가 내 과목인지, kind 가 study·play 중 하나인지는 Service 에서 검사합니다.
public record StudyPlanCreateRequest(
    @NotNull(message = "날짜를 보내 주세요") LocalDate date,
    // FE 의 id 는 글자라서 글자로 받습니다. 숫자만 받습니다. ("3" 은 되고 "abc" 는 안 됨)
    // 18자리까지만 받습니다. 그보다 길면 서버의 숫자(Long)에 안 들어가서 500 이 나기 때문입니다.
    @Pattern(regexp = "\\d{0,18}", message = "과목 번호가 올바르지 않아요") String subjectId,
    @NotBlank(message = "할 일을 입력해 주세요") @Size(max = 200, message = "할 일은 200자 이하로 적어 주세요")
        String content,
    // FE 의 타이머와 같은 범위입니다. (FE src/lib/countdown.ts 의 MIN·MAX_TARGET_MINUTES)
    @NotNull(message = "목표 시간을 보내 주세요")
        @Min(value = 1, message = "목표 시간은 1분 이상이어야 해요")
        @Max(value = 600, message = "목표 시간은 600분(10시간) 이하여야 해요")
        Integer targetMinutes,
    // "19:00" 처럼 두 자리:두 자리. 00:00 ~ 23:59 만 됩니다.
    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "시작 시각은 19:00 처럼 보내 주세요")
        String startTime,
    String kind) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public StudyPlanCreateRequest {
    content = content == null ? null : content.trim();
  }
}
