package com.ithopeschool.projectbe.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ithopeschool.projectbe.domain.suggestion.ExperimentJudge.DayMetric;
import com.ithopeschool.projectbe.domain.suggestion.ExperimentSummary.KeptCheck;
import com.ithopeschool.projectbe.domain.suggestion.ExperimentSummary.PeriodStats;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DayPlan;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 끝난 실험의 결과(판정과 근거 숫자) 계산 규칙(이슈 #54)을 확인합니다.
// 실험은 10월 15일에 시작한 14일짜리입니다. 기준선은 10월 1일 ~ 14일, 실험 기간은 10월 15일 ~ 28일입니다.
class ExperimentSummaryTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalDate START = LocalDate.of(2026, 10, 15);
  private static final int DAYS = 14;

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  private static DayPlan plan(int day, int targetMinutes) {
    return new DayPlan(october(day), targetMinutes);
  }

  // 10월 day일 hour:minute 에 시작해서 minutes 분 공부한 기록을 만듭니다. (일시정지 없음)
  private static DaySession study(int day, int hour, int minute, int minutes) {
    Instant startedAt = october(day).atTime(hour, minute).atZone(SEOUL).toInstant();
    return new DaySession(startedAt, startedAt.plusSeconds(minutes * 60L), 0, 0);
  }

  // 10월 from 일부터 to 일까지 매일 "계획 targetMinutes 분" 을 만듭니다.
  private static List<DayPlan> plansEveryDay(int from, int to, int targetMinutes) {
    List<DayPlan> plans = new ArrayList<>();
    for (int day = from; day <= to; day++) {
      plans.add(plan(day, targetMinutes));
    }
    return plans;
  }

  // 10월 from 일부터 to 일까지 매일 hour:minute 에 시작해서 minutes 분 공부한 기록을 만듭니다.
  private static List<DaySession> studyEveryDay(
      int from, int to, int hour, int minute, int minutes) {
    List<DaySession> sessions = new ArrayList<>();
    for (int day = from; day <= to; day++) {
      sessions.add(study(day, hour, minute, minutes));
    }
    return sessions;
  }

  // 10월 from 일부터 to 일까지 매일 같은 답(kept)을 한 이행 기록을 만듭니다.
  private static List<KeptCheck> checksEveryDay(int from, int to, boolean kept) {
    List<KeptCheck> checks = new ArrayList<>();
    for (int day = from; day <= to; day++) {
      checks.add(new KeptCheck(october(day), kept));
    }
    return checks;
  }

  // ─── 한꺼번에 계산하기 ───

  @Test
  void summarizesBaselineExperimentFulfillmentAndEffect() {
    // 기준선: 매일 계획 120분, 19:30 에 시작해 60분 / 실험: 매일 계획 120분, 19:00 에 시작해 90분
    List<DayPlan> plans = plansEveryDay(1, 28, 120);
    List<DaySession> sessions = new ArrayList<>();
    sessions.addAll(studyEveryDay(1, 14, 19, 30, 60));
    sessions.addAll(studyEveryDay(15, 28, 19, 0, 90));
    // 14일 중 11일을 지켰고 3일은 못 지켰다고 답했습니다.
    List<KeptCheck> checks = new ArrayList<>();
    checks.addAll(checksEveryDay(15, 25, true));
    checks.addAll(checksEveryDay(26, 28, false));

    ExperimentSummary summary = ExperimentSummary.of(START, DAYS, plans, sessions, checks);

    assertThat(summary)
        .isEqualTo(
            new ExperimentSummary(
                // 순공이 60분에서 90분으로 50% 늘었습니다.
                ExperimentEffect.EFFECTIVE,
                // 19:30 = 19 × 60 + 30 = 1170, 19:00 = 1140
                new PeriodStats(14, 60.0, 1170.0),
                new PeriodStats(14, 90.0, 1140.0),
                // 11 ÷ 14 = 78.6% → 79%
                new SuggestionRate(79, SuggestionGrade.GOOD)));
  }

  @Test
  void noRecordsAtAllMeansUnknownNotZero() {
    // 계획도 공부 기록도 이행 기록도 없으면 "0" 이 아니라 "모름" 입니다. 판정은 기록이 모자라서 애매함입니다.
    ExperimentSummary summary = ExperimentSummary.of(START, DAYS, List.of(), List.of(), List.of());

    assertThat(summary)
        .isEqualTo(
            new ExperimentSummary(
                ExperimentEffect.UNCLEAR,
                new PeriodStats(0, null, null),
                new PeriodStats(0, null, null),
                null));
  }

  // ─── 기간 ───

  @Test
  void baselineIsTheFourteenDaysRightBeforeTheStart() {
    // 9월 30일(기준선 하루 앞)과 10월 29일(실험 하루 뒤)의 기록은 어느 쪽에도 들어가지 않습니다.
    Instant september30 = LocalDate.of(2026, 9, 30).atTime(19, 0).atZone(SEOUL).toInstant();
    List<DaySession> sessions =
        List.of(
            new DaySession(september30, september30.plusSeconds(600 * 60L), 0, 0),
            study(1, 19, 0, 10),
            study(14, 19, 0, 30),
            study(15, 19, 0, 50),
            study(28, 19, 0, 70),
            study(29, 19, 0, 600));

    ExperimentSummary summary = ExperimentSummary.of(START, DAYS, List.of(), sessions, List.of());

    // 기준선: 10/1(10분) + 10/14(30분) → 평균 20분 / 실험: 10/15(50분) + 10/28(70분) → 평균 60분
    assertThat(summary.baseline()).isEqualTo(new PeriodStats(2, 20.0, 1140.0));
    assertThat(summary.experiment()).isEqualTo(new PeriodStats(2, 60.0, 1140.0));
  }

  @Test
  void dayFollowsTheFourAmRule() {
    // 10월 15일 새벽 1시에 시작한 공부는 10월 14일(기준선 마지막 날) 기록입니다. 시계 시각으로는 01:00 = 60분입니다.
    List<DaySession> sessions = List.of(study(15, 1, 0, 40));

    ExperimentSummary summary = ExperimentSummary.of(START, DAYS, List.of(), sessions, List.of());

    assertThat(summary.baseline()).isEqualTo(new PeriodStats(1, 40.0, 60.0));
    assertThat(summary.experiment()).isEqualTo(new PeriodStats(0, null, null));
  }

  // ─── 기록이 있는 날만 평균에 넣기 ───

  @Test
  void daysWithoutAnyRecordDoNotLowerTheAverage() {
    // 기준선 14일 중 3일만 공부했고 나머지 11일은 계획도 기록도 없습니다. 평균은 3일로 나눕니다.
    List<DaySession> sessions =
        List.of(study(2, 20, 0, 60), study(5, 20, 0, 90), study(9, 20, 0, 120));

    PeriodStats baseline =
        ExperimentSummary.of(START, DAYS, List.of(), sessions, List.of()).baseline();

    assertThat(baseline).isEqualTo(new PeriodStats(3, 90.0, 1200.0));
  }

  @Test
  void plannedDayWithoutStudyCountsAsZeroMinutes() {
    // 10/2 는 60분 공부, 10/3 은 계획만 있고 공부를 안 했습니다. 10/3 은 "0분인 날" 로 평균에 들어갑니다.
    // 첫 시작 시각은 공부를 시작한 날(10/2)만 셉니다.
    List<DayPlan> plans = List.of(plan(3, 120));
    List<DaySession> sessions = List.of(study(2, 18, 30, 60));

    PeriodStats baseline = ExperimentSummary.of(START, DAYS, plans, sessions, List.of()).baseline();

    assertThat(baseline).isEqualTo(new PeriodStats(2, 30.0, 1110.0));
  }

  @Test
  void averagesAreNotRounded() {
    // 10분 · 20분 · 25분 → 평균 18.33…분. 반올림은 보여줄 때 한 번만 합니다.
    List<DaySession> sessions =
        List.of(study(1, 19, 0, 10), study(2, 19, 10, 20), study(3, 19, 25, 25));

    PeriodStats baseline =
        ExperimentSummary.of(START, DAYS, List.of(), sessions, List.of()).baseline();

    assertThat(baseline.averageStudyMinutes()).isCloseTo(55.0 / 3, within(1e-9));
    // 19:00(1140) · 19:10(1150) · 19:25(1165) → 평균 1151.66…
    assertThat(baseline.averageFirstStartMinutes()).isCloseTo(3455.0 / 3, within(1e-9));
  }

  @Test
  void firstStartIsTheEarliestSessionOfEachDay() {
    // 하루에 여러 번 공부했으면 가장 이른 시작 시각만 봅니다. 10/1 은 08:00, 10/2 는 20:00 → 평균 14:00(840)
    List<DaySession> sessions =
        List.of(study(1, 21, 0, 30), study(1, 8, 0, 30), study(2, 20, 0, 30), study(2, 22, 0, 30));

    PeriodStats baseline =
        ExperimentSummary.of(START, DAYS, List.of(), sessions, List.of()).baseline();

    assertThat(baseline).isEqualTo(new PeriodStats(2, 60.0, 840.0));
  }

  // ─── 이행률 ───

  @Test
  void fulfillmentCountsOnlyChecksInsideTheExperimentPeriod() {
    // 실험 기간(10/15 ~ 10/28) 안: 지킴 7일, 못 지킴 3일 / 기간 밖(10/10 ~ 10/14, 10/29)의 "지킴" 은 세지 않습니다.
    List<KeptCheck> checks = new ArrayList<>();
    checks.addAll(checksEveryDay(10, 14, true));
    checks.addAll(checksEveryDay(15, 21, true));
    checks.addAll(checksEveryDay(22, 24, false));
    checks.add(new KeptCheck(october(29), true));

    ExperimentSummary summary = ExperimentSummary.of(START, DAYS, List.of(), List.of(), checks);

    // 7 ÷ 14 = 50% → 부분 시행. (답을 안 한 4일은 못 지킨 날로 셉니다)
    assertThat(summary.fulfillment()).isEqualTo(new SuggestionRate(50, SuggestionGrade.PARTIAL));
  }

  @Test
  void fulfillmentIsUnknownWhenAllChecksAreOutsideThePeriod() {
    List<KeptCheck> checks = checksEveryDay(1, 14, true);

    assertThat(ExperimentSummary.of(START, DAYS, List.of(), List.of(), checks).fulfillment())
        .isNull();
  }

  // ─── 효과 판정에 넣는 하루 지표 ───

  @Test
  void dayMetricsSkipEmptyDaysAndUseZeroForStudyWithoutPlan() {
    // 10/1: 계획 100분에 50분 공부 → 달성률 50
    // 10/2: 계획 없이 30분 공부 → 달성률을 계산할 수 없어서 0 으로 넣음
    // 10/3: 계획만 있고 공부 안 함 → 0분, 0%
    // 10/4: 아무 기록 없음 → 빠짐
    List<DayPlan> plans = List.of(plan(1, 100), plan(3, 60));
    List<DaySession> sessions = List.of(study(1, 19, 0, 50), study(2, 19, 0, 30));

    List<DayMetric> metrics =
        ExperimentSummary.dayMetricsOf(ExperimentSummary.datesFrom(october(1), 4), plans, sessions);

    assertThat(metrics)
        .containsExactly(new DayMetric(50, 50), new DayMetric(30, 0), new DayMetric(0, 0));
  }

  @Test
  void effectIsUnclearWhenEitherPeriodHasFewerThanFiveRecordedDays() {
    // 기준선은 4일뿐이고 실험은 14일 모두 기록이 있습니다. 한쪽이라도 5일 미만이면 단정하지 않습니다.
    List<DaySession> sessions = new ArrayList<>();
    sessions.addAll(studyEveryDay(1, 4, 19, 0, 60));
    sessions.addAll(studyEveryDay(15, 28, 19, 0, 180));

    assertThat(ExperimentSummary.of(START, DAYS, List.of(), sessions, List.of()).effect())
        .isEqualTo(ExperimentEffect.UNCLEAR);
  }

  @Test
  void effectIsNoEffectWhenNothingChanged() {
    List<DayPlan> plans = plansEveryDay(1, 28, 120);
    List<DaySession> sessions = studyEveryDay(1, 28, 19, 0, 60);

    assertThat(ExperimentSummary.of(START, DAYS, plans, sessions, List.of()).effect())
        .isEqualTo(ExperimentEffect.NO_EFFECT);
  }

  // ─── 날짜 목록 ───

  @Test
  void datesFromListsConsecutiveDays() {
    assertThat(ExperimentSummary.datesFrom(LocalDate.of(2026, 9, 29), 3))
        .containsExactly(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30), october(1));
    assertThat(ExperimentSummary.datesFrom(october(1), 0)).isEmpty();
    assertThat(ExperimentSummary.datesFrom(october(1), -3)).isEmpty();
  }
}
