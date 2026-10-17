package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// "카드 폼 기본값 추천" 요청의 모양입니다. (이슈 #237, FE #419)
//   { "title": "수학 인강 3강" }
// - title: 카드 폼에 쓴 제목 (카드 제목과 같이 100자까지)
// 목표 목록은 받지 않습니다. 서버가 DB 의 내 진행 중 목표를 읽습니다.
public record CardDefaultsRequest(
    @NotBlank(message = "카드 제목을 입력해 주세요") @Size(max = 100, message = "카드 제목은 100자 이하로 적어 주세요")
        String title) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public CardDefaultsRequest {
    title = title == null ? null : title.trim();
  }
}
