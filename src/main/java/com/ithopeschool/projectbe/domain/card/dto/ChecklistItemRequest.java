package com.ithopeschool.projectbe.domain.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 체크리스트 항목 하나의 요청 모양입니다. (FE 의 ChecklistItem)
//   { "id": "i1", "text": "수학 문제집 42~45쪽" }      { "text": "오답 정리" }
// - id 는 보내지 않아도 됩니다. 안 보내거나 빈 글자("")면 서버가 "i1", "i2" 처럼 겹치지 않게 만들어 줍니다.
// - 같은 카드 안에서 id 가 겹치면 Service 가 400 으로 돌려보냅니다.
public record ChecklistItemRequest(
    @Size(max = 40, message = "체크리스트 항목 id 는 40자 이하로 보내 주세요") String id,
    @NotBlank(message = "체크리스트 항목을 입력해 주세요") @Size(max = 100, message = "체크리스트 항목은 100자 이하로 적어 주세요")
        String text) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public ChecklistItemRequest {
    id = id == null ? null : id.trim();
    text = text == null ? null : text.trim();
  }
}
