package com.ithopeschool.projectbe.domain.cardresult.dto;

import java.time.LocalDate;

// 한 목표의 최근 4주 계획 패턴을 FE 에 알려 주는 모양입니다. (이슈 #229)
//   { "goalId": "12", "from": "2026-09-20", "to": "2026-10-17", "sampleCount": 8,
//     "checklistRate": 0.81, "pattern": "fit" }
// - sampleCount: 계산에 쓴 카드 하루치 수 (세션이 있었던 날만, 휴식 날은 뺌)
// - checklistRate: 그 카드 하루치들의 체크리스트 달성률 평균 (0 ~ 1). 하나도 없으면 null
// - pattern: over(과대) / fit(적정) / loose(느슨). 카드 하루치가 5개보다 적으면 아직 말하지 않아서 null
public record PlanPatternResponse(
    String goalId,
    LocalDate from,
    LocalDate to,
    int sampleCount,
    Double checklistRate,
    String pattern) {}
