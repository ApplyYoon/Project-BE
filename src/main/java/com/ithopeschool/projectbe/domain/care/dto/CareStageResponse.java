package com.ithopeschool.projectbe.domain.care.dto;

import com.ithopeschool.projectbe.domain.care.CareLevelInfo;
import com.ithopeschool.projectbe.domain.care.DailyCareStage;
import java.time.LocalDate;

// 하루의 관리 단계 응답입니다. (이슈 #62)
//   { "date": "2026-10-18", "stage": 2, "name": "넛지", "rule": "toBase",
//     "suggestShrink": false, "reason": "..." }
//
// date: 어느 날의 단계인지 / stage: 0 ~ 4 / name: 단계 이름 (CareLevelInfo)
// rule: 어느 규칙으로 정했는지 (safety · recovery · up · holdUnavoidable · holdOverload · holdUnknown ·
// toBase)
// suggestShrink: 계획을 줄여 보자고 제안할지 / reason: 왜 이 단계인지 한 줄
public record CareStageResponse(
    LocalDate date, int stage, String name, String rule, boolean suggestShrink, String reason) {

  public static CareStageResponse from(DailyCareStage saved) {
    return new CareStageResponse(
        saved.getStageDate(),
        saved.getStage(),
        CareLevelInfo.of(saved.getStage()).name(),
        saved.getRule().toValue(),
        saved.isSuggestShrink(),
        saved.getReason());
  }
}
