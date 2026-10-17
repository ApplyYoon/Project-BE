package com.ithopeschool.projectbe.domain.session.dto;

import com.ithopeschool.projectbe.domain.plan.PlanProgress;

// 계획 하나의 "실제 / 목표" 를 FE 에 알려 주는 모양입니다. 계획 줄의 "32 / 50분" 과 진행 막대에 씁니다.
//   { "planId": "7", "actualMinutes": 32, "percent": 64,
//     "isReached": false, "remainingMinutes": 18 }
// FE 의 PlanProgress (src/lib/planProgress.ts) 와 같은 이름·같은 규칙입니다. (계산은 PlanProgress 가 합니다)
// - planId 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - actualMinutes: 그 계획으로 켠 기록들의 순공 합(분). 1분이 안 되는 나머지는 버립니다.
// - percent: 목표 대비 진행률 (0 ~ 100, 소수점은 버립니다)
// - isReached: 목표 시간을 채웠으면 true
// - remainingMinutes: 목표까지 남은 시간(분, 올림). 채웠으면 0
public record PlanActualResponse(
    String planId, int actualMinutes, int percent, boolean isReached, int remainingMinutes) {

  public static PlanActualResponse of(Long planId, PlanProgress progress) {
    return new PlanActualResponse(
        String.valueOf(planId),
        progress.actualMinutes(),
        progress.percent(),
        progress.isReached(),
        progress.remainingMinutes());
  }
}
