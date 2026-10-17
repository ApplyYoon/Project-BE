package com.ithopeschool.projectbe.domain.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// "말로 계획 세우기" 요청의 모양입니다.
//   { "text": "내일 학원 끝나고 수학 문제집 2시간, 영어 단어 30분, 그다음 게임 1시간", "date": "2026-10-11" }
// - date: 초안을 넣을 날짜. 모든 초안이 이 날짜입니다. (GPT 가 날짜를 정하지 않습니다)
// - text: 계획을 말한 문장. 200자까지
public record PlanParseRequest(
    @NotNull(message = "날짜를 보내 주세요") LocalDate date,
    @NotBlank(message = "계획을 한 문장으로 적어 주세요") @Size(max = 200, message = "문장은 200자 이하로 적어 주세요")
        String text) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public PlanParseRequest {
    text = text == null ? null : text.trim();
  }
}
