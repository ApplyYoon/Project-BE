package com.ithopeschool.projectbe.domain.manual;

import com.ithopeschool.projectbe.domain.insight.Insight;
import com.ithopeschool.projectbe.domain.insight.InsightResponse;
import com.ithopeschool.projectbe.domain.suggestion.ExperimentEffect;
import java.time.LocalDate;
import java.util.Collections;

// "나 사용설명서" 에 쌓이는 검증된 사실 한 줄입니다. (이슈 #46)
// 여기서는 효과가 확인된 실험을 사용설명서 항목으로 바꾸는 일을 합니다.
//
// 정해 둔 규칙
//   1. 효과 판정이 "효과 있음" 인 실험만 올릴 수 있습니다. 애매함·효과 없음은 올리지 않습니다. (canRegister)
//      검증된 것만 쌓여야 "이 앱이 나를 안다" 는 믿음이 생깁니다.
//   2. 제목은 개선안 문장 그대로입니다.   "만약 공부를 시작하면 → 폰은 다른 방에 둔다"
//   3. 근거에는 어떤 실험이었는지(기간)와 숫자를 적습니다.
//        실험 전 2주와 실험 중의 하루 평균 순공을 둘 다 알 때   →  "14일 실험: 하루 순공 +35분"
//        둘 중 하나라도 모를 때 (기록이 모자람)                →  "14일 실험으로 효과 확인"
//      차이는 (실험 중 − 실험 전) 을 반올림한 값입니다. 줄었으면 "-12분", 같으면 부호 없이 "0분" 입니다.
//   4. 확인한 날짜는 사용설명서에 올린 날(오늘)입니다.
//
// (위의 규칙은 옛 기획 v2 의 실험, 곧 순공시간 비교용입니다. FE 의 buildManualItem 이 있던
//  src/lib/experimentResult.ts 는 지금 FE 에 없습니다.)
//
// ■ GET /api/v1/manual 이 쓰는 규칙 (v3 자기 실험 + 인사이트, 이슈 #46)
//   - 효과 있음으로 판정된 실험 (ofCardExperiment): 붙인 카드의 실행률로 판정한 v3 실험입니다.
//       제목: 전략의 실험 규칙 한 줄   "시작할 때 폰을 가방에 넣기"
//       근거: "14일 실험: 카드 실행률 50% → 70% (+20%p)"   숫자를 모르면 "14일 실험으로 효과 확인"
//       확인한 날짜: 판정한 날
//   - 확실성 높은 인사이트 (canRegisterInsight, ofInsight)
//       "가설" 이 아닌 것만: 비교하는 조건이 모두 5일 이상이어야 합니다. 학생이 "틀렸어" 를 누른 것은 올리지 않습니다.
//       제목: 인사이트 문장 그대로 / 확인한 날짜: 보여 준 날 / 근거: "근거 9일 (10/01 ~ 10/16)"
//
// category: 분류 / title: 한 줄 제목 / confirmedDate: 이 사실이 확인된 날짜 / evidence: 근거
public record ManualItem(
    ManualCategory category, String title, LocalDate confirmedDate, String evidence) {

  // 이 실험을 사용설명서에 올려도 되면 true 입니다. 효과 판정이 "효과 있음" 일 때만입니다. (규칙 1)
  public static boolean canRegister(ExperimentEffect effect) {
    return effect == ExperimentEffect.EFFECTIVE;
  }

  // 효과가 확인된 실험을 사용설명서 항목으로 바꿉니다. (규칙 2 ~ 4)
  //
  // ifText, thenText: 개선안의 "만약 ~하면" 과 "~한다" 문장
  // days: 실험 기간 일수 (연장했으면 연장한 뒤의 일수)
  // baselineAverageMinutes: 실험 직전 2주의 하루 평균 순공(분). 기록이 없어서 모르면 null
  // experimentAverageMinutes: 실험 중의 하루 평균 순공(분). 모르면 null
  // today: 사용설명서에 올리는 날 (하루의 기준인 새벽 4시로 정한 날짜, DayRule)
  public static ManualItem ofExperiment(
      String ifText,
      String thenText,
      int days,
      Double baselineAverageMinutes,
      Double experimentAverageMinutes,
      LocalDate today) {
    String evidence = days + "일 실험으로 효과 확인";
    if (baselineAverageMinutes != null && experimentAverageMinutes != null) {
      long diff = Math.round(experimentAverageMinutes - baselineAverageMinutes);
      String sign = diff > 0 ? "+" : diff < 0 ? "-" : "";
      evidence = days + "일 실험: 하루 순공 " + sign + Math.abs(diff) + "분";
    }

    return new ManualItem(
        ManualCategory.EFFECTIVE_METHOD, "만약 " + ifText + " → " + thenText, today, evidence);
  }

  // 효과 있음으로 판정된 v3 자기 실험을 사용설명서 항목으로 바꿉니다.
  //
  // rule: 카드에 붙였던 실험 규칙 한 줄 (ExperimentStrategy.rule)
  // days: 실험 기간 일수
  // baselineRate, experimentRate: 판정 때의 평균 실행률(0 ~ 1). 모르면 null
  // confirmedDate: 판정한 날 (하루의 기준인 새벽 4시로 정한 날짜, DayRule)
  //
  //   기준선 0.50, 실험 0.70  →  "14일 실험: 카드 실행률 50% → 70% (+20%p)"
  //   기준선 0.50, 실험 0.58  →  "14일 실험: 카드 실행률 50% → 58% (+8%p)"
  public static ManualItem ofCardExperiment(
      String rule, int days, Double baselineRate, Double experimentRate, LocalDate confirmedDate) {
    String evidence = days + "일 실험으로 효과 확인";
    if (baselineRate != null && experimentRate != null) {
      // 화면에 보이는 두 퍼센트의 차이를 적어야 숫자가 서로 맞아서, 먼저 반올림한 뒤 뺍니다.
      long baselinePercent = Math.round(baselineRate * 100);
      long experimentPercent = Math.round(experimentRate * 100);
      long diff = experimentPercent - baselinePercent;
      String sign = diff > 0 ? "+" : diff < 0 ? "-" : "";
      evidence =
          days
              + "일 실험: 카드 실행률 "
              + baselinePercent
              + "% → "
              + experimentPercent
              + "% ("
              + sign
              + Math.abs(diff)
              + "%p)";
    }

    return new ManualItem(ManualCategory.EFFECTIVE_METHOD, rule, confirmedDate, evidence);
  }

  // 이 인사이트를 사용설명서에 올려도 되면 true 입니다. 확실성 높은 것만입니다.
  //   - "가설"(비교 조건 중 하나라도 5일 미만)이면 올리지 않습니다.
  //   - 학생이 "틀렸어" 를 눌렀으면 올리지 않습니다.
  //   - 근거 날짜가 없으면 올리지 않습니다. (항목마다 근거가 붙어야 합니다)
  public static boolean canRegisterInsight(Insight insight) {
    return !insight.isHypothesis()
        && insight.getReaction() != InsightResponse.WRONG
        && !insight.getEvidenceDates().isEmpty();
  }

  // 인사이트를 사용설명서 항목으로 바꿉니다. 올려도 되는 인사이트만 넘겨 주세요. (canRegisterInsight)
  //
  //   근거 날짜 9개, 10/01 ~ 10/16  →  "근거 9일 (10/01 ~ 10/16)"
  public static ManualItem ofInsight(Insight insight, ManualCategory category) {
    LocalDate first = Collections.min(insight.getEvidenceDates());
    LocalDate last = Collections.max(insight.getEvidenceDates());
    String evidence =
        "근거 "
            + insight.getEvidenceDates().size()
            + "일 ("
            + monthDay(first)
            + " ~ "
            + monthDay(last)
            + ")";

    return new ManualItem(category, insight.getSentence(), insight.getShownDate(), evidence);
  }

  // 10/01 처럼 월/일만 두 자리로 적습니다.
  private static String monthDay(LocalDate date) {
    return String.format("%02d/%02d", date.getMonthValue(), date.getDayOfMonth());
  }
}
