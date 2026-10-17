package com.ithopeschool.projectbe.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.report.GrowthCurve.GrowthPoint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 예상 성장 곡선 계산 규칙(이슈 #45)을 확인합니다.
// feExample 로 시작하는 3개는 FE 함수(getGrowthCurve) 주석의 예시를 그대로 옮긴 것입니다.
class GrowthCurveTest {

  // 테스트에서 쓰는 "오늘": 2026년 10월 12일
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

  // 매일 같은 시간을 공부한 기록을 만듭니다. 예: everyDay(10, 60) → 10일 동안 매일 60분
  private static List<Integer> everyDay(int days, int minutes) {
    List<Integer> daily = new ArrayList<>();
    for (int i = 0; i < days; i++) {
      daily.add(minutes);
    }
    return daily;
  }

  // 앞의 기간과 뒤의 기간을 이어 붙입니다. (맨 뒤가 오늘입니다)
  private static List<Integer> join(List<Integer> earlier, List<Integer> later) {
    List<Integer> daily = new ArrayList<>(earlier);
    daily.addAll(later);
    return daily;
  }

  // ─── FE 예시 ───

  @Test
  void feExample1FiveDaysAreNotEnough() {
    // FE 예1) 기록이 5일치뿐인 경우 → hasEnoughData: false (나머지 값은 비어 있음)
    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(30), everyDay(5, 60));

    assertThat(curve).isEqualTo(GrowthCurve.EMPTY);
    assertThat(curve.hasEnoughData()).isFalse();
    assertThat(curve.actual()).isEmpty();
  }

  @Test
  void feExample2PaceAndExtraScenarios() {
    // FE 예2) 10일치 기록, 하루 평균 60분, 시험일이 10일 뒤인 경우
    //         → 지금 페이스 = 지금까지 누적(600분) + 60분 × 10일 = 1,200분
    //         → 하루 20분 더 = 지금까지 누적(600분) + 80분 × 10일 = 1,400분
    LocalDate examDate = TODAY.plusDays(10);

    GrowthCurve curve = GrowthCurve.of(TODAY, examDate, everyDay(10, 60));

    assertThat(curve.hasEnoughData()).isTrue();
    assertThat(curve.currentPaceTotalMinutes()).isEqualTo(1200);
    assertThat(curve.withExtraTotalMinutes()).isEqualTo(1400);
    // 두 시나리오 모두 "오늘의 누적" 에서 시작해 시험일의 예상 누적으로 끝나는 두 점입니다.
    assertThat(curve.currentPace())
        .containsExactly(new GrowthPoint(TODAY, 600), new GrowthPoint(examDate, 1200));
    assertThat(curve.withExtra())
        .containsExactly(new GrowthPoint(TODAY, 600), new GrowthPoint(examDate, 1400));
  }

  @Test
  void feExample3TwentyDaysHaveNoFourWeekComparison() {
    // FE 예3) 기록이 20일치뿐인 경우 (28일 미만) → hasFourWeekComparison: false
    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(30), everyDay(20, 60));

    assertThat(curve.hasEnoughData()).isTrue();
    assertThat(curve.hasFourWeekComparison()).isFalse();
    assertThat(curve.dailyMinutesChangeFromFourWeeksAgo()).isEqualTo(0);
  }

  // ─── 그래프를 보여줄 만큼 쌓였는지 ───

  @Test
  void sevenDaysAreEnough() {
    // 경계값: 6일이면 숨기고, 7일부터 보여줍니다.
    assertThat(GrowthCurve.of(TODAY, TODAY.plusDays(30), everyDay(6, 60)).hasEnoughData())
        .isFalse();
    assertThat(GrowthCurve.of(TODAY, TODAY.plusDays(30), everyDay(7, 60)).hasEnoughData()).isTrue();
  }

  @Test
  void noRecordsMeansEmptyCurve() {
    // 기록이 하나도 없으면 빈 결과입니다.
    assertThat(GrowthCurve.of(TODAY, TODAY.plusDays(30), List.of())).isEqualTo(GrowthCurve.EMPTY);
  }

  // ─── 실제 누적선 ───

  @Test
  void actualLineIsCumulativeFromTheFirstRecordDay() {
    // 누적선은 첫 기록 날짜부터 오늘까지 하루에 한 점씩이고, 값은 그날까지 더한 순공시간입니다.
    // 7일치: 30, 0, 60, 0, 0, 90, 20 → 누적 30, 30, 90, 90, 90, 180, 200
    List<Integer> daily = List.of(30, 0, 60, 0, 0, 90, 20);

    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(30), daily);

    assertThat(curve.actual())
        .containsExactly(
            new GrowthPoint(LocalDate.of(2026, 10, 6), 30),
            new GrowthPoint(LocalDate.of(2026, 10, 7), 30),
            new GrowthPoint(LocalDate.of(2026, 10, 8), 90),
            new GrowthPoint(LocalDate.of(2026, 10, 9), 90),
            new GrowthPoint(LocalDate.of(2026, 10, 10), 90),
            new GrowthPoint(LocalDate.of(2026, 10, 11), 180),
            new GrowthPoint(TODAY, 200));
  }

  // ─── 지금 페이스 ───

  @Test
  void paceLooksAtTheLastFourteenDaysOnly() {
    // "지금 페이스" 는 최근 14일의 평균입니다. 그 전에 얼마나 했는지는 평균에 넣지 않습니다.
    // 앞의 10일은 매일 300분, 최근 14일은 매일 60분 → 페이스는 60분
    List<Integer> daily = join(everyDay(10, 300), everyDay(14, 60));

    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(10), daily);

    // 누적 3,000 + 840 = 3,840분. 지금 페이스: 3,840 + 60 × 10 = 4,440분
    assertThat(curve.currentPaceTotalMinutes()).isEqualTo(4440);
    assertThat(curve.withExtraTotalMinutes()).isEqualTo(4640);
  }

  @Test
  void paceCountsDaysWithoutStudyAsZero() {
    // 공부를 안 한 날도 평균에 0 으로 들어갑니다. 10일 중 5일만 120분 → 하루 평균 60분
    List<Integer> daily = List.of(120, 0, 120, 0, 120, 0, 120, 0, 120, 0);

    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(10), daily);

    assertThat(curve.currentPaceTotalMinutes()).isEqualTo(600 + 60 * 10);
  }

  @Test
  void extraScenarioAddsTwentyMinutesPerRemainingDay() {
    // 두 시나리오의 차이는 언제나 "남은 날 × 20분" 입니다. 시험이 37일 뒤면 740분 차이
    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(37), everyDay(14, 45));

    assertThat(curve.withExtraTotalMinutes() - curve.currentPaceTotalMinutes()).isEqualTo(37 * 20);
  }

  @Test
  void projectedTotalIsRounded() {
    // 평균이 소수로 나와도 마지막에 한 번만 반올림합니다.
    // 7일 동안 합계 100분 → 하루 평균 14.28…분. 시험이 3일 뒤 → 100 + 42.85… = 142.85… → 143분
    List<Integer> daily = List.of(100, 0, 0, 0, 0, 0, 0);

    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(3), daily);

    assertThat(curve.currentPaceTotalMinutes()).isEqualTo(143);
    // 하루 20분 더: 100 + 34.28… × 3 = 202.85… → 203분
    assertThat(curve.withExtraTotalMinutes()).isEqualTo(203);
  }

  // ─── 시험일 ───

  @Test
  void examTodayOrPastMeansNoRemainingDays() {
    // 남은 날은 오늘을 빼고 셉니다. 시험이 오늘이거나 이미 지났으면 0일이라 예상 누적이 지금 누적과 같습니다.
    GrowthCurve today = GrowthCurve.of(TODAY, TODAY, everyDay(10, 60));
    GrowthCurve past = GrowthCurve.of(TODAY, TODAY.minusDays(5), everyDay(10, 60));

    assertThat(today.currentPaceTotalMinutes()).isEqualTo(600);
    assertThat(today.withExtraTotalMinutes()).isEqualTo(600);
    assertThat(past.currentPaceTotalMinutes()).isEqualTo(600);
    assertThat(past.withExtraTotalMinutes()).isEqualTo(600);
  }

  @Test
  void examTomorrowIsOneRemainingDay() {
    // 시험이 내일이면 남은 날은 1일입니다.
    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(1), everyDay(10, 60));

    assertThat(curve.currentPaceTotalMinutes()).isEqualTo(660);
    assertThat(curve.withExtraTotalMinutes()).isEqualTo(680);
  }

  // ─── 4주 전의 나 ───

  @Test
  void twentyEightDaysAllowFourWeekComparison() {
    // 경계값: 27일이면 비교를 숨기고, 28일부터 보여줍니다.
    assertThat(GrowthCurve.of(TODAY, TODAY.plusDays(30), everyDay(27, 60)).hasFourWeekComparison())
        .isFalse();
    assertThat(GrowthCurve.of(TODAY, TODAY.plusDays(30), everyDay(28, 60)).hasFourWeekComparison())
        .isTrue();
  }

  @Test
  void comparesTheLastWeekWithTheWeekFourWeeksAgo() {
    // 28일치: 맨 앞 7일(4주 전)은 매일 30분, 가운데 14일은 매일 200분, 최근 7일은 매일 75분
    // → 최근 7일 평균 75분 − 4주 전 7일 평균 30분 = +45분 (가운데 14일은 보지 않습니다)
    List<Integer> daily = join(join(everyDay(7, 30), everyDay(14, 200)), everyDay(7, 75));

    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(30), daily);

    assertThat(curve.hasFourWeekComparison()).isTrue();
    assertThat(curve.dailyMinutesChangeFromFourWeeksAgo()).isEqualTo(45);
  }

  @Test
  void changeIsNegativeWhenStudyingLess() {
    // 4주 전보다 줄었으면 음수입니다. 과장도 숨김도 없이 그대로 보여줍니다.
    List<Integer> daily = join(join(everyDay(7, 120), everyDay(14, 60)), everyDay(7, 50));

    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(30), daily);

    assertThat(curve.dailyMinutesChangeFromFourWeeksAgo()).isEqualTo(-70);
  }

  @Test
  void fourWeeksAgoIsCountedBackFromToday() {
    // 기록이 28일보다 길면 "4주 전" 은 맨 앞이 아니라 오늘에서 28일 전부터의 7일입니다.
    // 40일치: 맨 앞 12일은 매일 500분(보지 않음), 그다음 7일은 매일 10분(4주 전), 14일은 0분, 최근 7일은 40분
    List<Integer> daily =
        join(join(join(everyDay(12, 500), everyDay(7, 10)), everyDay(14, 0)), everyDay(7, 40));

    GrowthCurve curve = GrowthCurve.of(TODAY, TODAY.plusDays(30), daily);

    assertThat(curve.dailyMinutesChangeFromFourWeeksAgo()).isEqualTo(30);
  }
}
