package com.ithopeschool.projectbe.domain.goal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// "목표 고치기" 요청의 모양입니다. 바꿀 것만 보냅니다. (PATCH) 보내지 않은(null) 값은 그대로 둡니다.
//   목표값 고치기 : { "title": "11월 학평 수학 4등급", "reason": "시간이 모자라서" }   ← 이유 필수
//   WOOP 고치기   : { "plan": "폰을 가방에 넣기" }
//   완료         : { "status": "done", "result": "4등급 받음" }
//   포기         : { "status": "givenUp", "givenUpReason": "목표를 바꾸기로 해서" }   ← 이유 필수
//   다시 진행     : { "status": "active" }
// - 목표값(title · dDay · currentScore · totalAmount · amountUnit)이 하나라도 바뀌면 reason 이 꼭 있어야 하고,
//   바뀌기 전 값이 이력(history)에 남습니다. 진행 중인 목표만 목표값을 바꿀 수 있습니다.
// - 무엇이 바뀌었는지, 유형에 맞는 칸인지, 상태 글자가 맞는지는 Service 에서 검사합니다.
public record GoalUpdateRequest(
    @Size(min = 1, max = 40, message = "목표는 1자 이상 40자 이하로 적어 주세요") String title,
    LocalDate dDay,
    @Size(min = 1, max = 20, message = "지금 점수는 1자 이상 20자 이하로 적어 주세요") String currentScore,
    @Min(value = 1, message = "전체 분량을 숫자로 적어 주세요")
        @Max(value = 100000, message = "전체 분량은 100000 이하로 적어 주세요")
        Integer totalAmount,
    @Size(min = 1, max = 5, message = "단위는 1자 이상 5자 이하로 적어 주세요") String amountUnit,
    // 목표값을 바꾸는 이유
    @Size(max = 100, message = "바꾸는 이유는 100자 이하로 적어 주세요") String reason,
    @Size(min = 1, max = 100, message = "이루면 좋은 점은 1자 이상 100자 이하로 적어 주세요") String benefit,
    @Size(min = 1, max = 100, message = "안 하면 생길 일은 1자 이상 100자 이하로 적어 주세요") String cost,
    @Size(min = 1, max = 100, message = "방해물은 1자 이상 100자 이하로 적어 주세요") String obstacle,
    @Size(min = 1, max = 100, message = "대처는 1자 이상 100자 이하로 적어 주세요") String plan,
    // active · done · givenUp
    String status,
    @Size(max = 100, message = "결과는 100자 이하로 적어 주세요") String result,
    @Size(max = 100, message = "포기하는 이유는 100자 이하로 적어 주세요") String givenUpReason) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. "   " 처럼 공백뿐인 글은 빈 글자("")가 되어 위의 1자 이상 검사에 걸립니다.
  // 이유 · 결과는 빈 글이면 없는 것(null)으로 봅니다. (이유가 꼭 필요한 경우는 Service 가 "없음" 으로 보고 400)
  public GoalUpdateRequest {
    title = GoalCreateRequest.trim(title);
    currentScore = GoalCreateRequest.trim(currentScore);
    amountUnit = GoalCreateRequest.trim(amountUnit);
    reason = GoalCreateRequest.blankToNull(reason);
    benefit = GoalCreateRequest.trim(benefit);
    cost = GoalCreateRequest.trim(cost);
    obstacle = GoalCreateRequest.trim(obstacle);
    plan = GoalCreateRequest.trim(plan);
    result = GoalCreateRequest.blankToNull(result);
    givenUpReason = GoalCreateRequest.blankToNull(givenUpReason);
  }

  // 목표값 다섯 칸 중 하나라도 보냈는지
  public boolean hasTarget() {
    return title != null
        || dDay != null
        || currentScore != null
        || totalAmount != null
        || amountUnit != null;
  }

  // WOOP 글 네 칸 중 하나라도 보냈는지
  public boolean hasWoop() {
    return benefit != null || cost != null || obstacle != null || plan != null;
  }

  // 아무것도 보내지 않았는지 (reason 하나만 보낸 것도 "바꿀 것 없음" 입니다)
  public boolean isEmpty() {
    return !hasTarget() && !hasWoop() && status == null && result == null && givenUpReason == null;
  }
}
