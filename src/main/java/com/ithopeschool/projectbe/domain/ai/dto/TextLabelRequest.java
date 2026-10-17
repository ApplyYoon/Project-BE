package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 글에 이름표 붙이기 요청의 모양입니다. (이슈 #237 표 7번)
//   '기타' 사유:  { "kind": "reason", "text": "동생이 방에 들어와서 떠들었다" }
//   딴생각 메모:  { "kind": "memo", "text": "엄마한테 학원비 문자 보내기" }
// - kind: reason(일시정지·실패 사유의 '기타' 글) 또는 memo(딴생각 주차장 메모)
// - text: 학생이 쓴 글. 100자까지
public record TextLabelRequest(
    @NotNull(message = "글의 종류를 보내 주세요")
        @Pattern(regexp = "reason|memo", message = "글의 종류는 reason 또는 memo 로 보내 주세요")
        String kind,
    @NotBlank(message = "분류할 글을 보내 주세요") @Size(max = 100, message = "글은 100자 이하로 보내 주세요")
        String text) {

  public static final String KIND_REASON = "reason";
  public static final String KIND_MEMO = "memo";

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public TextLabelRequest {
    text = text == null ? null : text.trim();
  }
}
