package com.ithopeschool.projectbe.domain.cardresult.dto;

import com.ithopeschool.projectbe.domain.cardresult.CardDay;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.CardResultJudge;
import com.ithopeschool.projectbe.domain.cardresult.PlanAxis;
import java.time.LocalDate;

// 카드 하루치(그 카드 · 그 날짜)의 판정 결과 하나를 FE 에 알려 주는 모양입니다. (이슈 #229)
//   { "cardId": "7", "date": "2026-10-12", "result": "late", "runRate": 0.75,
//     "planAxis": "execution", "checklistRate": 0.5, "predictionGap": -1, "startedOnTime": false }
// - cardId 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - result 가 null 이면 아직 판정할 수 없는 카드입니다 (카드 시간 전이고 세션도 없음).
// - runRate 는 0 ~ 1. 미실행(미루기 · 실패 · 미처리)이면 0 입니다.
// - planAxis · checklistRate · predictionGap 은 판정에 넣는 세션이 없으면 null 입니다.
// - startedOnTime 은 시작하지 않았거나 사후 입력뿐이거나 시작 시각이 없는 카드면 null 입니다.
public record CardDayResultResponse(
    String cardId,
    LocalDate date,
    String result,
    double runRate,
    String planAxis,
    Double checklistRate,
    Integer predictionGap,
    Boolean startedOnTime) {

  // 카드 번호와 카드 하루치, 그 결과로 응답 한 줄을 만듭니다. 숫자 계산은 모두 CardResultJudge 가 합니다.
  public static CardDayResultResponse from(Long cardId, CardDay day, CardResult result) {
    // 미실행은 실행률 0 으로 셉니다. (기획서 「5.3」 "미실행 0")
    double runRate = result != null && result.isMiss() ? 0 : CardResultJudge.getRunRate(day);
    PlanAxis planAxis = CardResultJudge.getPlanAxis(day);
    Double checklistRate =
        CardResultJudge.hasCountedSession(day) ? CardResultJudge.getChecklistRate(day) : null;

    return new CardDayResultResponse(
        String.valueOf(cardId),
        day.date(),
        result == null ? null : result.toValue(),
        runRate,
        planAxis == null ? null : planAxis.toValue(),
        checklistRate,
        CardResultJudge.getPredictionGap(day),
        CardResultJudge.isStartedOnTime(day));
  }
}
