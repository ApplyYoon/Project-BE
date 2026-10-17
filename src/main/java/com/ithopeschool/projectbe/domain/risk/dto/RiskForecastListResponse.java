package com.ithopeschool.projectbe.domain.risk.dto;

import com.ithopeschool.projectbe.domain.risk.RiskForecast;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 기간의 위험 예측 목록과 "얼마나 맞았는지" 입니다.
//   { "from": "2026-10-01", "to": "2026-10-31", "forecasts": [ ... ], "confirmedCount": 12,
// "correctCount": 9 }
// - forecasts: 날짜 순서. 예측을 한 날만 있습니다. (안 한 날은 줄이 없습니다)
// - confirmedCount: 그날이 지나서 맞았는지 확인된 예측의 수
// - correctCount: 그중 맞은 수. confirmedCount 가 0 이면 아직 정확도를 말할 수 없습니다.
public record RiskForecastListResponse(
    LocalDate from,
    LocalDate to,
    List<RiskForecastResponse> forecasts,
    int confirmedCount,
    int correctCount) {

  public static RiskForecastListResponse of(
      LocalDate from, LocalDate to, List<RiskForecast> forecasts) {
    List<RiskForecastResponse> items = new ArrayList<>();
    int confirmed = 0;
    int correct = 0;
    for (RiskForecast forecast : forecasts) {
      items.add(RiskForecastResponse.from(forecast));
      if (forecast.getWasCorrect() != null) {
        confirmed++;
        if (forecast.getWasCorrect()) {
          correct++;
        }
      }
    }
    return new RiskForecastListResponse(from, to, items, confirmed, correct);
  }
}
