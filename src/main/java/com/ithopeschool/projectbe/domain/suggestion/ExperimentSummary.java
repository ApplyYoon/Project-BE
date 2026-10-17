package com.ithopeschool.projectbe.domain.suggestion;

import com.ithopeschool.projectbe.domain.suggestion.ExperimentJudge.DayMetric;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DayPlan;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

// 끝난 실험의 결과를 한꺼번에 계산합니다. (이슈 #54 "판정 결과와 근거 숫자" 의 계산 부분)
// 판정(효과 있음·없음·애매함)만 보여주면 왜 그렇게 나왔는지 알 수 없어서, 비교한 숫자를 같이 만듭니다.
//
//   실험 전 2주(기준선)  vs  실험 중
//     하루 평균 순공시간 · 평균 첫 시작 시각 · 기록이 있는 날 수
//   이행률(지킨 날 ÷ 실험 전체 일수)과 이행 판정
//   효과 판정 (ExperimentJudge)
//
// 정해 둔 규칙
//   1. 기준선은 실험 시작 직전 14일, 실험 기간은 시작일부터 days 일입니다. (ExperimentProgress)
//        10월 15일 시작, 14일  →  기준선 10/1 ~ 10/14, 실험 10/15 ~ 10/28
//   2. "기록이 있는 날" 만 평균에 넣습니다. 공부 기록이 있거나 그날 계획이 있는 날입니다.
//      아무 기록도 없는 날은 "0분 공부한 날" 이 아니라 "앱을 안 쓴 날" 이라, 평균을 깎지 않게 뺍니다.
//      계획은 있는데 공부를 안 한 날은 0분으로 넣습니다.
//   3. 평균은 반올림하지 않은 소수 그대로 돌려줍니다. 보여줄 때 한 번만 반올림합니다.
//   4. 평균 첫 시작 시각은 "그날 0시부터 몇 분" 입니다. 공부를 시작한 날만 셉니다. (19:25 → 1165)
//   5. 이행률은 실험 기간 안의 이행 기록만 셉니다. 하나도 없으면 "0%" 가 아니라 null(아직 모름) 입니다. (SuggestionRate)
//   6. 효과 판정에 넣는 하루 달성률은, 계획 없이 공부만 한 날(계산 불가)을 0 으로 넣습니다. (FE 와 같음)
//
// 예시 (10월 15일 시작, 14일)
//   기준선: 매일 계획 120분, 매일 19:30 에 시작해 60분 공부      →  14일, 하루 평균 60분, 평균 첫 시작 1170(19:30)
//   실험:   매일 계획 120분, 매일 19:00 에 시작해 90분 공부      →  14일, 하루 평균 90분, 평균 첫 시작 1140(19:00)
//   14일 중 11일을 지켰다고 답함                               →  이행률 79%, 잘 시행
//   순공이 50% 늘어서                                         →  효과 있음
//
// FE 의 getPeriodStats · summarizeExperiment (src/lib/experimentResult.ts),
// getEffectResult (src/lib/careInputs.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 결과표가 어긋나지 않습니다.
//
// effect: 효과 판정
// baseline: 실험 직전 2주의 평균 / experiment: 실험 중의 평균
// fulfillment: 이행률(%)과 판정. 실험 중 이행 기록이 하나도 없으면 null
public record ExperimentSummary(
    ExperimentEffect effect,
    PeriodStats baseline,
    PeriodStats experiment,
    SuggestionRate fulfillment) {

  // 배포 서버는 보통 UTC 로 돌아가서, 서버 컴퓨터의 시간대에 기대지 않고 한국 시간을 직접 지정합니다.
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 한 기간(실험 전 2주 또는 실험 중)의 평균입니다.
  // recordedDays: 기록이 있는 날 수 (공부 기록이 있거나 그날 계획이 있는 날)
  // averageStudyMinutes: 기록이 있는 날의 하루 평균 순공시간(분). 기록이 있는 날이 없으면 null
  // averageFirstStartMinutes: 공부를 시작한 날들의 평균 첫 시작 시각(그날 0시부터 몇 분). 시작한 날이 없으면 null
  public record PeriodStats(
      int recordedDays, Double averageStudyMinutes, Double averageFirstStartMinutes) {}

  // 그 개선안을 그날 지켰는지 적은 기록 하나입니다.
  // date: 지켰는지 묻는 날 / kept: 지켰으면 true
  // (개선안·이행 기록 테이블은 아직 없어서, 엔티티가 아니라 이 계산에 필요한 두 값만 받습니다)
  public record KeptCheck(LocalDate date, boolean kept) {}

  // 끝난 실험 하나의 결과를 계산합니다.
  //
  // startDate: 실험을 시작한 날 / days: 실험 기간 일수 (예: 14)
  // plans, sessions: 기준선 첫날부터 실험 마지막 날까지의 계획과 공부 기록. 다른 날짜가 섞여 있어도 됩니다.
  //   노는 계획과 노는 기록은 공부가 아니라서, 부르는 쪽에서 빼고 넘깁니다.
  // checks: 이 개선안의 이행 기록. 다른 개선안의 기록은 부르는 쪽에서 빼고 넘깁니다. 실험 기간 밖의 날짜는 이 함수가 뺍니다.
  public static ExperimentSummary of(
      LocalDate startDate,
      int days,
      List<DayPlan> plans,
      List<DaySession> sessions,
      List<KeptCheck> checks) {
    List<LocalDate> baselineDates =
        datesFrom(ExperimentProgress.baselineStartOf(startDate), ExperimentProgress.BASELINE_DAYS);
    List<LocalDate> experimentDates = datesFrom(startDate, days);

    // 효과 판정: 두 기간의 "기록이 있는 날" 을 하루 지표로 바꿔서 비교합니다.
    ExperimentEffect effect =
        ExperimentJudge.judge(
            dayMetricsOf(baselineDates, plans, sessions),
            dayMetricsOf(experimentDates, plans, sessions));

    // 이행률: 실험 기간 안에 적은 기록만 셉니다. (규칙 5)
    List<Boolean> keptList = new ArrayList<>();
    for (KeptCheck check : checks) {
      if (experimentDates.contains(check.date())) {
        keptList.add(check.kept());
      }
    }

    return new ExperimentSummary(
        effect,
        periodStatsOf(baselineDates, plans, sessions),
        periodStatsOf(experimentDates, plans, sessions),
        SuggestionRate.of(keptList, days));
  }

  // 날짜들의 평균을 계산합니다. 기록이 없는 날은 평균을 깎지 않게 뺍니다. (규칙 2 ~ 4)
  public static PeriodStats periodStatsOf(
      List<LocalDate> dates, List<DayPlan> plans, List<DaySession> sessions) {
    long studyMinutesSum = 0;
    int recordedDays = 0;
    long firstStartMinutesSum = 0;
    int startedDays = 0;

    for (LocalDate date : dates) {
      DaySummary summary = DaySummary.of(date, plans, sessions);
      if (!hasRecord(date, summary, plans)) {
        continue;
      }
      studyMinutesSum += summary.studyMinutes();
      recordedDays++;

      if (summary.firstStartAt() != null) {
        // 한국 시간의 시계 시각을 "0시부터 몇 분" 으로 바꿉니다. (19:25 → 19 × 60 + 25 = 1165)
        ZonedDateTime first = summary.firstStartAt().atZone(SEOUL);
        firstStartMinutesSum += first.getHour() * 60 + first.getMinute();
        startedDays++;
      }
    }

    // 정수끼리 나누면 소수점이 버려져서, 한쪽을 소수(double)로 바꿔서 나눕니다.
    Double averageStudyMinutes = recordedDays == 0 ? null : (double) studyMinutesSum / recordedDays;
    Double averageFirstStartMinutes =
        startedDays == 0 ? null : (double) firstStartMinutesSum / startedDays;
    return new PeriodStats(recordedDays, averageStudyMinutes, averageFirstStartMinutes);
  }

  // 날짜들 중 "기록이 있는 날" 만 골라, 효과 판정에 넣을 하루 지표(순공시간·달성률)로 바꿉니다. (규칙 2, 6)
  public static List<DayMetric> dayMetricsOf(
      List<LocalDate> dates, List<DayPlan> plans, List<DaySession> sessions) {
    List<DayMetric> metrics = new ArrayList<>();
    for (LocalDate date : dates) {
      DaySummary summary = DaySummary.of(date, plans, sessions);
      if (!hasRecord(date, summary, plans)) {
        continue;
      }
      // 계획 없이 공부만 한 날은 달성률을 계산할 수 없어서(null) 0 으로 넣습니다.
      int achievementRate = summary.achievementRate() == null ? 0 : summary.achievementRate();
      metrics.add(new DayMetric(summary.studyMinutes(), achievementRate));
    }
    return metrics;
  }

  // startDate 부터 count 일 동안의 날짜 목록을 만듭니다. count 가 0 이하면 비어 있습니다.
  //
  //   (10월 1일, 3)  →  [10월 1일, 10월 2일, 10월 3일]
  public static List<LocalDate> datesFrom(LocalDate startDate, int count) {
    List<LocalDate> dates = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      dates.add(startDate.plusDays(index));
    }
    return dates;
  }

  // 그날이 "기록이 있는 날" 인지 봅니다. 공부 기록이 있거나, 그날 계획이 있으면 기록이 있는 날입니다.
  private static boolean hasRecord(LocalDate date, DaySummary summary, List<DayPlan> plans) {
    if (summary.studyMinutes() > 0) {
      return true;
    }
    for (DayPlan plan : plans) {
      if (plan.date().equals(date)) {
        return true;
      }
    }
    return false;
  }
}
