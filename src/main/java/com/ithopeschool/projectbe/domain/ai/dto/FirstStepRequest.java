package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 첫 준비 행동 요청의 모양입니다. (이슈 #236)
//   처음:        { "item": "42~45쪽 문제 풀기", "title": "수학 문제집", "level": 0 }
//   [더 작게]:   { "item": "42~45쪽 문제 풀기", "title": "수학 문제집", "level": 1, "previousStep": "수학 문제집 42쪽
// 펴기" }
// - item        : 필수. 체크리스트 첫 항목, 100자까지
// - title       : 선택. 카드 제목, 200자까지 (공부 계획의 내용과 같은 길이)
// - level       : 선택. [더 작게] 를 누른 횟수 0 ~ 2. 안 보내면 0
// - previousStep: 선택. [더 작게] 를 누르기 전에 보여 준 행동, 40자까지. 이것보다 작게 만듭니다.
public record FirstStepRequest(
    @NotBlank(message = "체크리스트 첫 항목을 보내 주세요") @Size(max = 100, message = "첫 항목은 100자 이하로 보내 주세요")
        String item,
    @Size(max = 200, message = "카드 제목은 200자 이하로 적어 주세요") String title,
    @Min(value = 0, message = "단계는 0 ~ 2 사이로 보내 주세요")
        @Max(value = 2, message = "단계는 0 ~ 2 사이로 보내 주세요")
        Integer level,
    @Size(max = 40, message = "앞의 행동은 40자 이하로 보내 주세요") String previousStep) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 빈 글은 없는 것(null)으로, 단계를 안 보냈으면 0 으로 봅니다.
  public FirstStepRequest {
    item = item == null ? null : item.trim();
    title = title == null || title.isBlank() ? null : title.trim();
    level = level == null ? 0 : level;
    previousStep = previousStep == null || previousStep.isBlank() ? null : previousStep.trim();
  }
}
