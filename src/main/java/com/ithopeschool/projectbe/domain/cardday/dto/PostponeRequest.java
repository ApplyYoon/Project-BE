package com.ithopeschool.projectbe.domain.cardday.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// "카드 미루기" 요청의 모양입니다. (최종 기획서 「4.4 타이머」 미루기와 미실행, 이슈 #225)
//   { "date": "2026-10-12", "reason": "(왜 미루는지 + 어떻게 할지, 100자 이상)",
//     "newDate": "2026-10-13", "newStartTime": "20:00" }
// - date: 어느 날의 카드를 미루는지 (반복 카드는 한 장이라 날짜가 꼭 있어야 합니다)
// - reason: 앞뒤 공백을 뺀 길이가 100 ~ 500자. 일부러 쓰기 번거롭게 한 장치(의도된 마찰)입니다.
// - newDate · newStartTime: 새로 하기로 한 날짜와 시각. 지금보다 뒤여야 합니다. (Service 에서 검사)
public record PostponeRequest(
    @NotNull(message = "어느 날의 카드를 미룰지 보내 주세요") LocalDate date,
    @NotNull(message = "왜 미루는지와 어떻게 할지를 적어 주세요")
        @Size(min = 100, max = 500, message = "왜 미루는지와 어떻게 할지를 100자 이상 500자 이하로 적어 주세요")
        String reason,
    @NotNull(message = "새로 할 날짜를 보내 주세요") LocalDate newDate,
    @NotNull(message = "새로 할 시각을 보내 주세요")
        @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "새 시각은 19:00 처럼 보내 주세요")
        String newStartTime) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 100자 검사는 공백을 지운 길이로 돌아갑니다.
  public PostponeRequest {
    reason = reason == null ? null : reason.trim();
  }
}
