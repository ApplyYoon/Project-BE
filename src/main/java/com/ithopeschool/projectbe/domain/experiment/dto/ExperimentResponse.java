package com.ithopeschool.projectbe.domain.experiment.dto;

import com.ithopeschool.projectbe.domain.experiment.Experiment;
import com.ithopeschool.projectbe.domain.experiment.ExperimentRule;
import com.ithopeschool.projectbe.domain.suggestion.SuggestionRate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 자기 실험 하나의 응답입니다. (FE 의 Experiment 와 같은 이름 + 서버가 계산한 칸)
//   { "id": "3", "hypothesis": "…", "cause": "phone", "strategyId": "phone-bag",
//     "rule": "시작할 때 폰을 가방에 넣기", "cardIds": ["12"], "startDate": "2026-10-01",
//     "endDate": "2026-10-14", "day": 15, "minGainPp": 10, "status": "effective",
//     "baselineRate": 0.5, "experimentRate": 0.7, "fulfillment": { "rate": 86, "grade": "good" } }
//
// day: 며칠째인지 (시작한 날 = 1, 끝났으면 15)
// baselineRate · experimentRate: 판정 때의 평균 실행률(0 ~ 1). 진행 중이거나 카드가 모자라면 null
// fulfillment: 이행률 (붙인 카드를 실행한 비율, %)과 판정(good · partial · poor). 셀 카드가 없으면 null
public record ExperimentResponse(
    String id,
    String hypothesis,
    String cause,
    String strategyId,
    String rule,
    List<String> cardIds,
    LocalDate startDate,
    LocalDate endDate,
    int day,
    int minGainPp,
    String status,
    Double baselineRate,
    Double experimentRate,
    Fulfillment fulfillment) {

  public record Fulfillment(int rate, String grade) {

    static Fulfillment from(SuggestionRate rate) {
      return rate == null ? null : new Fulfillment(rate.rate(), rate.grade().name().toLowerCase());
    }
  }

  public static ExperimentResponse from(
      Experiment experiment, LocalDate today, SuggestionRate fulfillment) {
    List<String> cardIds = new ArrayList<>();
    for (Long cardId : experiment.getCardIds()) {
      cardIds.add(String.valueOf(cardId));
    }
    return new ExperimentResponse(
        String.valueOf(experiment.getId()),
        experiment.getHypothesis(),
        experiment.getCause() == null ? null : experiment.getCause().toValue(),
        experiment.getStrategy().getId(),
        experiment.getStrategy().getRule(),
        cardIds,
        experiment.getStartDate(),
        ExperimentRule.endDate(experiment.getStartDate()),
        ExperimentRule.dayOf(experiment.getStartDate(), today),
        experiment.getMinGainPp(),
        experiment.getStatus().getValue(),
        experiment.getBaselineRate(),
        experiment.getExperimentRate(),
        Fulfillment.from(fulfillment));
  }
}
