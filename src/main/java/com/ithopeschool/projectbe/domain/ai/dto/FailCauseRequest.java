package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 예상 실패 원인 선지 요청의 모양입니다. (이슈 #237 표 3번)
//   { "title": "수학 문제집" }
// - title: 카드 제목. 200자까지 (공부 계획의 내용과 같은 길이)
// 지난 실패 원인은 보내지 않습니다. 서버가 DB 에 쌓인 내 기록(최근 30일 실패 원인)에서 찾습니다.
public record FailCauseRequest(
    @NotBlank(message = "카드 제목을 적어 주세요") @Size(max = 200, message = "카드 제목은 200자 이하로 적어 주세요")
        String title) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public FailCauseRequest {
    title = title == null ? null : title.trim();
  }
}
