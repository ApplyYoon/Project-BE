package com.ithopeschool.projectbe.domain.manual;

import com.ithopeschool.projectbe.domain.suggestion.ExperimentEffect;
import java.time.LocalDate;

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
// FE 의 buildManualItem (src/lib/experimentResult.ts) 과 같은 규칙·같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 사용설명서가 어긋나지 않습니다.
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
}
