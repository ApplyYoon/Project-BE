package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// 루틴 카드 제안 요청의 모양입니다. (이슈 #239, 기획서 4.7 "P 계획 → AI 가 루틴 카드 2~3장으로 바꿔 제안")
//   { "goal": "영단어장 1회독", "obstacle": "폰을 자꾸 보게 돼요", "plan": "폰을 다른 방에 두기",
//     "dDay": "2026-10-26", "totalAmount": 300, "amountUnit": "쪽" }
// - goal       : 목표 (WOOP 의 W). 100자까지
// - obstacle   : 내 안의 방해물 (O). 없으면 빼거나 null. 100자까지
// - plan       : 방해물이 생겼을 때의 대처 (P). 100자까지
// - dDay · totalAmount · amountUnit : 완주형 목표일 때만. 셋 다 있으면 서버가 하루 분량을 계산합니다. (숫자는 코드가)
// 목표는 아직 서버에 저장하지 않아서(#221) 번호 대신 글을 받습니다.
public record RoutineCardRequest(
    @NotBlank(message = "목표를 적어 주세요") @Size(max = 100, message = "목표는 100자 이하로 적어 주세요") String goal,
    @Size(max = 100, message = "방해물은 100자 이하로 적어 주세요") String obstacle,
    @NotBlank(message = "대처를 적어 주세요") @Size(max = 100, message = "대처는 100자 이하로 적어 주세요") String plan,
    LocalDate dDay,
    @Min(value = 1, message = "전체 분량은 1 ~ 100000 사이로 보내 주세요")
        @Max(value = 100000, message = "전체 분량은 1 ~ 100000 사이로 보내 주세요")
        Integer totalAmount,
    @Size(max = 5, message = "분량 단위는 5자 이하로 보내 주세요") String amountUnit) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 선택 칸이 빈 글이면 없는 것(null)으로 봅니다.
  public RoutineCardRequest {
    goal = goal == null ? null : goal.trim();
    obstacle = obstacle == null || obstacle.isBlank() ? null : obstacle.trim();
    plan = plan == null ? null : plan.trim();
    amountUnit = amountUnit == null || amountUnit.isBlank() ? null : amountUnit.trim();
  }
}
