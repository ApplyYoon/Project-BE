package com.ithopeschool.projectbe.domain.suggestion;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 개선안 실험이 "지금 어디까지 왔는지" 를 날짜로 계산합니다. (이슈 #52, 기획서 v2 4장 「제시 방식」)
// 효과를 판정하려면 기간 중에는 전략을 바꾸지 않아야 해서, 실험이 며칠째인지·끝났는지를 한 곳에서 정합니다.
//
//   10월 1일 시작, 14일짜리 실험
//     실험 기간      10월 1일 ~ 10월 14일
//     결과가 나오는 날  10월 15일 (마지막 날의 다음 날. 이날부터 "끝난" 실험입니다)
//     기준선 기간    9월 17일 ~ 9월 30일 (직전 2주)
//
//   오늘이 10월 5일   →  5일째, 이행률을 셀 수 있는 지난 날은 4일(10/1 ~ 10/4)
//   그 4일을 모두 지켰다고 답함  →  이행률 100 (4 ÷ 4)
//
// 진행 중의 이행률은 "지난 날 중 지킨 날" 로 셉니다. 실험이 끝난 뒤의 이행률(SuggestionRate, 전체 일수 기준)과 다릅니다.
// 진행 중에 아직 안 온 날을 못 지킨 날로 세면 초반에 늘 낮게 나와서 잘못 혼내게 되기 때문입니다.
//
// FE 의 getExperimentProgress · splitActiveExperiments (src/lib/experimentResult.ts),
// BASELINE_DAYS (src/lib/careInputs.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 "며칠째" 가 어긋나지 않습니다.
//
// totalDays: 실험 기간 전체 일수
// dayIndex: 오늘이 며칠째인지 (1부터. 아직 시작 전이면 0, 끝났으면 totalDays 로 맞춥니다)
// resultDate: 결과가 나오는 날. 실험 마지막 날의 다음 날입니다.
// keptDays: 지금까지 "어제 지켰어?" 에 지켰다고 답한 날 수
// elapsedDays: 이행률을 셀 수 있는 지난 날 수 (시작일부터 어제까지). 첫날이면 0
// rate: 지금까지의 이행률(%). 아직 셀 날이 없으면 null
public record ExperimentProgress(
    int totalDays,
    int dayIndex,
    LocalDate resultDate,
    int keptDays,
    int elapsedDays,
    Integer rate) {

  // 실험 효과를 비교할 기준선: 실험 시작 직전 며칠 (기획서: 직전 2주)
  public static final int BASELINE_DAYS = 14;

  // 결과가 나오는 날을 돌려줍니다. 실험 마지막 날의 다음 날입니다.
  //
  //   10월 1일 시작, 14일  →  10월 15일
  public static LocalDate resultDateOf(LocalDate startDate, int days) {
    return startDate.plusDays(days);
  }

  // 실험 기간이 끝났으면 true 를 돌려줍니다. 오늘이 결과가 나오는 날이거나 그 뒤일 때입니다.
  //
  //   10월 1일 시작, 14일  →  10월 14일은 아직 진행 중(false), 10월 15일부터 끝난 실험(true)
  public static boolean isFinished(LocalDate startDate, int days, LocalDate today) {
    return !today.isBefore(resultDateOf(startDate, days));
  }

  // 기준선 기간의 첫날을 돌려줍니다. 기준선은 이날부터 실험 시작 전날까지 14일입니다.
  //
  //   10월 1일 시작  →  9월 17일 (기준선 9월 17일 ~ 9월 30일)
  public static LocalDate baselineStartOf(LocalDate startDate) {
    return startDate.minusDays(BASELINE_DAYS);
  }

  // 실험이 지금 어디까지 왔는지 계산합니다.
  //
  // startDate: 실험을 시작한 날 / days: 실험 기간 일수 (예: 14)
  // keptDates: 이 개선안을 "지켰다" 고 답한 날짜들. 날짜마다 한 번씩만 넣습니다.
  //   다른 개선안의 답이나 "못 지켰다" 고 답한 날은 부르는 쪽에서 빼고 넘깁니다.
  //   실험 기간 밖의 날짜나 오늘 날짜가 섞여 있어도 됩니다. 이 함수가 시작일부터 어제까지만 셉니다.
  // today: 오늘 날짜 (하루의 기준인 새벽 4시로 정한 날짜, DayRule)
  public static ExperimentProgress of(
      LocalDate startDate, int days, List<LocalDate> keptDates, LocalDate today) {
    // 시작일부터 오늘까지 며칠이 지났는지 셉니다. (오늘이 시작일이면 1일째)
    int passed = (int) ChronoUnit.DAYS.between(startDate, today) + 1;
    int dayIndex = Math.min(days, Math.max(0, passed));

    // 어제까지의 날들 중 지킨 날을 셉니다. (오늘은 아직 끝나지 않아서 세지 않습니다)
    int elapsedDays = Math.min(days, Math.max(0, passed - 1));
    // 셀 수 있는 마지막 날의 다음 날입니다. 이날부터는 세지 않습니다.
    LocalDate countUntil = startDate.plusDays(elapsedDays);
    int keptDays = 0;
    for (LocalDate keptDate : keptDates) {
      if (!keptDate.isBefore(startDate) && keptDate.isBefore(countUntil)) {
        keptDays++;
      }
    }

    // 먼저 나누고 100 을 곱한 뒤 반올림합니다. (FE 와 같은 순서)
    Integer rate =
        elapsedDays == 0 ? null : (int) Math.round((double) keptDays / elapsedDays * 100);

    return new ExperimentProgress(
        days, dayIndex, resultDateOf(startDate, days), keptDays, elapsedDays, rate);
  }
}
