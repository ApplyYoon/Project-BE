package com.ithopeschool.projectbe.domain.risk.dto;

import com.ithopeschool.projectbe.domain.risk.RiskForecast;
import com.ithopeschool.projectbe.domain.risk.RiskLevel;
import com.ithopeschool.projectbe.domain.risk.RiskReason;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 위험 예측 하나를 FE 에 알려 주는 모양입니다.
//   { "date": "2026-10-13", "riskLevel": "HIGH",
//     "reasons": [ { "code": "WEAK_WEEKDAY", "label": "약한 요일" }, { "code": "LATE_SLEEP", "label":
// "늦게 잠" } ],
//     "actualAchievementRate": null, "wasCorrect": null }
// - riskLevel 은 NONE(없음) / WATCH(주의) / HIGH(높음) 입니다. 위험한 날은 WATCH 와 HIGH 입니다.
// - reasons 는 위험하다고 본 이유들입니다. 위험 없음이면 빈 목록입니다. label 은 화면에 그대로 써도 되는 한국어입니다.
// - actualAchievementRate · wasCorrect 는 그날이 지나고 채워집니다. 그전에는 null 입니다.
//   그날 계획이 없어서 달성률을 구할 수 없었던 날도 null 입니다. (맞았는지 알 수 없음)
// - 누구의 예측인지(userId)는 담지 않습니다.
public record RiskForecastResponse(
    LocalDate date,
    RiskLevel riskLevel,
    List<Reason> reasons,
    Integer actualAchievementRate,
    Boolean wasCorrect) {

  // 이유 하나. code 는 FE 가 아이콘 등을 고를 때, label 은 글자로 보여 줄 때 씁니다.
  public record Reason(RiskReason code, String label) {}

  public static RiskForecastResponse from(RiskForecast forecast) {
    List<Reason> reasons = new ArrayList<>();
    // Set 은 enum 선언 순서(약한 요일 → 늦게 잠)로 돌아서 응답 순서가 늘 같습니다.
    for (RiskReason reason : forecast.getReasons()) {
      reasons.add(new Reason(reason, reason.getLabel()));
    }
    return new RiskForecastResponse(
        forecast.getForecastDate(),
        forecast.getRiskLevel(),
        reasons,
        forecast.getActualAchievementRate(),
        forecast.getWasCorrect());
  }
}
