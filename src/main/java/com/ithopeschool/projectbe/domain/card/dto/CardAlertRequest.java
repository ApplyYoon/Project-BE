package com.ithopeschool.projectbe.domain.card.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 카드 알림의 요청 모양입니다. (FE 의 CardAlert)
//   { "method": "call", "minutesBefore": 10, "text": "수학 시작할 시간이야" }
// - method 는 "message" / "call" 중 하나입니다. (Service 에서 검사)
// - minutesBefore 는 시작 몇 분 전인지 (0 ~ 120). 0 이면 정각
// - text 는 보내지 않거나 빈 글자("")면 기본 문구를 씁니다.
public record CardAlertRequest(
    @NotBlank(message = "알림 방법을 보내 주세요") String method,
    @NotNull(message = "몇 분 전에 알릴지 보내 주세요")
        @Min(value = 0, message = "알림은 0분 전(정각)부터 정할 수 있어요")
        @Max(value = 120, message = "알림은 120분 전까지만 정할 수 있어요")
        Integer minutesBefore,
    @Size(max = 100, message = "알림 문구는 100자 이하로 적어 주세요") String text) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public CardAlertRequest {
    text = text == null ? null : text.trim();
  }
}
