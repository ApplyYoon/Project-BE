package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// "긴 계획 고치기" 요청의 모양입니다. (2026-10-18)
//   { "plan": { …/ai/long-plan 에서 받은 plan 그대로… }, "request": "주말은 빼 줘" }
// - plan    : 필수. 지금 계획의 뼈대 (LongPlanOutline)
// - request : 필수. 학생의 요청 한 줄 (100자). [줄여 줘] 같은 칩도 이 글로 보냅니다.
public record LongPlanReviseRequest(
    @NotNull(message = "지금 계획을 보내 주세요") @Valid LongPlanOutline plan,
    @NotBlank(message = "어떻게 고칠지 적어 주세요") @Size(max = 100, message = "요청은 100자 이하로 보내 주세요")
        String request) {

  // 요청 앞뒤 공백을 지웁니다.
  public LongPlanReviseRequest {
    request = request == null ? null : request.trim();
  }
}
