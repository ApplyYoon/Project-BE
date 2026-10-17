package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 말로 설정 바꾸기 요청의 모양입니다. (이슈 #237 표 8번)
//   { "text": "아침엔 알림 꺼 줘" }
// - text: 학생의 부탁 한 줄. 100자까지
// 지금 설정은 보내지 않습니다. 서버가 DB 에 저장된 내 관리 설정을 읽습니다.
public record SettingsFromTextRequest(
    @NotBlank(message = "바꾸고 싶은 것을 적어 주세요") @Size(max = 100, message = "부탁은 100자 이하로 적어 주세요")
        String text) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public SettingsFromTextRequest {
    text = text == null ? null : text.trim();
  }
}
