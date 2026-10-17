package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 즉석 카드 요청의 모양입니다. (이슈 #237 표 6번)
//   { "text": "수학 하자" }
// - text: 학생이 쓴 한 줄. 100자까지
// 내 과목과 최근 카드 제목은 보내지 않습니다. 서버가 DB 에서 찾습니다.
public record QuickCardRequest(
    @NotBlank(message = "한 줄을 적어 주세요") @Size(max = 100, message = "한 줄은 100자 이하로 적어 주세요")
        String text) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public QuickCardRequest {
    text = text == null ? null : text.trim();
  }
}
