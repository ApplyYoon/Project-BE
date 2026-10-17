package com.ithopeschool.projectbe.domain.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 딴생각 주차장 메모 한 줄의 요청 모양입니다. (FE 의 SessionMemo)
//   { "at": 1790000700000, "text": "엄마한테 문자 보내기" }
public record SessionMemoRequest(
    @NotNull(message = "메모를 쓴 시각을 보내 주세요") Long at,
    @NotBlank(message = "메모를 입력해 주세요") @Size(max = 100, message = "메모는 100자 이하로 적어 주세요")
        String text) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public SessionMemoRequest {
    text = text == null ? null : text.trim();
  }
}
