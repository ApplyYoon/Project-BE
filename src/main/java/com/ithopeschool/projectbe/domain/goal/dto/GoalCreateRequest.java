package com.ithopeschool.projectbe.domain.goal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// "목표 만들기" 요청의 모양입니다. (FE 의 Goal 에서 id · status · history · createdAt 등을 뺀 것)
//   점수형: { "kind": "score", "title": "11월 학평 수학 3등급", "dDay": "2026-11-19", "currentScore": "5등급",
//            "benefit": "…", "cost": "…", "obstacle": "폰을 자꾸 보게 돼요", "plan": "폰을 다른 방에 두기" }
//   완주형: { "kind": "completion", "title": "영단어장 1회독", "dDay": "2026-10-26",
//            "totalAmount": 300, "amountUnit": "쪽", "benefit": "…", … }
// - 점수형은 currentScore 가, 완주형은 totalAmount · amountUnit 이 꼭 있어야 합니다.
//   유형에 맞지 않는 칸은 버립니다. (FE 의 buildGoal 과 같음)
// - kind 가 정해진 값인지, D-day 가 내일 이후인지, 유형별 칸이 있는지는 Service 에서 검사합니다. 문장은 FE 와 같습니다.
public record GoalCreateRequest(
    @NotNull(message = "목표 유형을 골라 주세요") String kind,
    @NotBlank(message = "목표를 한 줄로 적어 주세요") @Size(max = 40, message = "목표는 40자 이하로 적어 주세요")
        String title,
    @NotNull(message = "D-day 는 내일 이후로 골라 주세요") LocalDate dDay,
    @Size(max = 20, message = "지금 점수는 20자 이하로 적어 주세요") String currentScore,
    @Min(value = 1, message = "전체 분량을 숫자로 적어 주세요")
        @Max(value = 100000, message = "전체 분량은 100000 이하로 적어 주세요")
        Integer totalAmount,
    @Size(max = 5, message = "단위는 5자 이하로 적어 주세요") String amountUnit,
    @NotBlank(message = "이루면 좋은 점을 적어 주세요") @Size(max = 100, message = "이루면 좋은 점은 100자 이하로 적어 주세요")
        String benefit,
    @NotBlank(message = "안 하면 생길 일을 적어 주세요")
        @Size(max = 100, message = "안 하면 생길 일은 100자 이하로 적어 주세요")
        String cost,
    @NotBlank(message = "내 안의 방해물을 하나 골라 주세요") @Size(max = 100, message = "방해물은 100자 이하로 적어 주세요")
        String obstacle,
    @NotBlank(message = "방해물이 생기면 어떻게 할지 적어 주세요") @Size(max = 100, message = "대처는 100자 이하로 적어 주세요")
        String plan) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  // 선택 칸(지금 점수 · 단위)이 빈 글이면 없는 것(null)으로 봅니다.
  public GoalCreateRequest {
    title = trim(title);
    currentScore = blankToNull(currentScore);
    amountUnit = blankToNull(amountUnit);
    benefit = trim(benefit);
    cost = trim(cost);
    obstacle = trim(obstacle);
    plan = trim(plan);
  }

  // 앞뒤 공백 지우기 (null 은 그대로). 바꾸기 요청도 같이 씁니다.
  static String trim(String text) {
    return text == null ? null : text.trim();
  }

  // 앞뒤 공백을 지우고, 남는 게 없으면 null
  static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }
}
