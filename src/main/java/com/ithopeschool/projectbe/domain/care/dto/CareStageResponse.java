package com.ithopeschool.projectbe.domain.care.dto;

import com.ithopeschool.projectbe.domain.care.CareLevelInfo;
import com.ithopeschool.projectbe.domain.care.DailyCareStage;
import java.time.LocalDate;

// 하루의 관리 단계 응답입니다. (이슈 #62, 4단계 칸은 #234)
//   { "date": "2026-10-18", "stage": 4, "name": "강제적 채찍", "rule": "up",
//     "suggestShrink": false, "reason": "...", "released": false,
//     "forcedCard": { "cardId": 7, "date": "2026-10-18" } }
//
// date: 어느 날의 단계인지 / stage: 지금의 단계 0 ~ 4 (4단계를 풀었으면 3) / name: 단계 이름 (CareLevelInfo)
// rule: 어느 규칙으로 정했는지 (safety · recovery · up · holdUnavoidable · holdOverload · holdUnknown ·
// toBase)
// suggestShrink: 계획을 줄여 보자고 제안할지 / reason: 왜 이 단계인지 한 줄 (풀었으면 푼 이유)
// released: 4단계를 증빙이 있는 실행으로 풀었는지
// forcedCard: 4단계라서 그날 하루만 절대 카드로 보는 카드. 없으면 null
//   ⚠️ 제재 한도를 4 밑으로 낮췄거나 보호 조치 중이면 4단계 장치는 꺼집니다. 서버는 미루기 · 증빙에서 다시 확인합니다.
public record CareStageResponse(
    LocalDate date,
    int stage,
    String name,
    String rule,
    boolean suggestShrink,
    String reason,
    boolean released,
    ForcedCard forcedCard) {

  // 그날 하루만 절대 카드로 보는 카드 (cardId: 카드 번호 / date: 그 날짜)
  public record ForcedCard(Long cardId, LocalDate date) {}

  public static CareStageResponse from(DailyCareStage saved) {
    ForcedCard forcedCard =
        saved.getForcedCardId() == null
            ? null
            : new ForcedCard(saved.getForcedCardId(), saved.getForcedCardDate());
    return new CareStageResponse(
        saved.getStageDate(),
        saved.currentStage(),
        CareLevelInfo.of(saved.currentStage()).name(),
        saved.getRule().toValue(),
        saved.isSuggestShrink(),
        saved.currentReason(),
        saved.isReleased(),
        forcedCard);
  }
}
