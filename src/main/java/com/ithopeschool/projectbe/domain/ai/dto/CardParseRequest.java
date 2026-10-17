package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// "말로 카드 만들기" 요청의 모양입니다. (이슈 #237, FE #419)
//   { "date": "2026-10-17", "text": "내일 7시에 수학 문제집 2장, 영어 단어 30분" }
// - date: 기준 날짜 (보통 오늘). 문장의 "내일" · "토요일" 은 이 날짜에서 셉니다.
// - text: 학생이 말하거나 쓴 문장 (200자까지, 옛 "말로 계획 세우기" 와 같음)
public record CardParseRequest(
    @NotNull(message = "기준 날짜를 보내 주세요") LocalDate date,
    @NotBlank(message = "어떤 카드를 만들지 말해 주세요") @Size(max = 200, message = "문장은 200자 이하로 보내 주세요")
        String text) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public CardParseRequest {
    text = text == null ? null : text.trim();
  }
}
