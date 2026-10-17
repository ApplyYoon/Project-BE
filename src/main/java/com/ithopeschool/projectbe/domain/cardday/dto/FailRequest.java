package com.ithopeschool.projectbe.domain.cardday.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// "실패로 기록" 요청의 모양입니다. (최종 기획서 「4.4 타이머」 미루기와 미실행, 이슈 #225)
//   { "date": "2026-10-12", "cause": "procrastinated", "reason": "숙제가 늦게 끝났다" }
// - cause: 원인 태그 (필수, 1탭). 8가지 중 하나
//     tired · event · phone · tooMuch · unwilling · sick · procrastinated(미루다 보니) · etc
// - reason: 직접 쓴 사유 (선택, 500자까지)
public record FailRequest(
    @NotNull(message = "어느 날의 카드인지 보내 주세요") LocalDate date,
    @NotBlank(message = "실패 원인을 골라 주세요") String cause,
    @Size(max = 500, message = "실패 사유는 500자 이하로 적어 주세요") String reason) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public FailRequest {
    reason = reason == null ? null : reason.trim();
  }
}
