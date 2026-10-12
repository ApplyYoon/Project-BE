package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactPlan;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactSession;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 주 단위 계산 규칙(이슈 #44)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/weeklyFacts.ts) 주석의 예시를 그대로 옮긴 것입니다.
class WeeklyFactsTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final long MINUTE = 60_000;

  // FE 예시의 주: 2026-10-05(월) ~ 2026-10-11(일)
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 한국 시간 "month월 day일 hour시" 를 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int month, int day, int hour) {
    return ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, SEOUL).toInstant();
  }

  // 그날 저녁 7시에 시작해서 minutes 분 공부한 기록을 만듭니다. (계획·목표 없음)
  private static FactSession study(int month, int day, int minutes) {
    return new FactSession(null, at(month, day, 19), minutes * MINUTE, 0);
  }

  private static FactPlan plan(long id, LocalDate date, int targetMinutes) {
    return new FactPlan(id, date, targetMinutes, null);
  }

  // ─── mondayOf: 그 주의 월요일 ───

  @Test
  void feExampleMondayOf() {
    // FE 예) 10월 10일 토요일 15시 → 그 주 월요일(10/05)
    assertThat(WeeklyFacts.mondayOf(at(10, 10, 15))).isEqualTo(october(5));
    // FE 예) 10월 11일 일요일 23시 → 10/05 (일요일은 그 주의 마지막 날)
    assertThat(WeeklyFacts.mondayOf(at(10, 11, 23))).isEqualTo(october(5));
    // FE 예) 10월 12일 월요일 9시 → 10/12 (월요일 9시는 새 주)
    assertThat(WeeklyFacts.mondayOf(at(10, 12, 9))).isEqualTo(october(12));
    // FE 예) 10월 12일 월요일 새벽 2시 → 10/05 (아직 일요일)
    assertThat(WeeklyFacts.mondayOf(at(10, 12, 2))).isEqualTo(october(5));
  }

  @Test
  void everyDayOfTheWeekGoesBackToItsMonday() {
    // 월요일부터 일요일까지 7일이 모두 같은 월요일로 돌아갑니다. 그다음 날은 다음 주입니다.
    for (int day = 5; day <= 11; day++) {
      assertThat(WeeklyFacts.mondayOf(october(day))).as("10월 %d일", day).isEqualTo(october(5));
    }
    assertThat(WeeklyFacts.mondayOf(october(12))).isEqualTo(october(12));
  }

  @Test
  void mondayCanBeInThePreviousMonth() {
    // 달이 바뀌는 주도 맞게 돌아갑니다. 10월 1일(목)의 월요일은 9월 28일입니다.
    assertThat(WeeklyFacts.mondayOf(october(1))).isEqualTo(LocalDate.of(2026, 9, 28));
  }

  // ─── of: 이번 주와 그 전 주 ───

  @Test
  void feExampleThisWeekUntilToday() {
    // FE 예) 오늘이 2026-10-07(수), 이번 주 월 60분 · 화 0분 · 수 90분 공부
    //        → thisWeek 는 10/05 ~ 10/07, lastWeek 는 09/28 ~ 10/04 를 계산
    //        → dailyAverageMinutes 50   ((60 + 0 + 90) ÷ 3일)
    List<FactSession> sessions = List.of(study(10, 5, 60), study(10, 7, 90), study(9, 30, 120));

    WeeklyFacts weekly = WeeklyFacts.of(List.of(), sessions, MONDAY, october(7));

    assertThat(weekly.thisWeek().studyMinutes()).isEqualTo(150);
    assertThat(weekly.lastWeek().studyMinutes()).isEqualTo(120);
    assertThat(weekly.dailyAverageMinutes()).isEqualTo(50);
  }

  @Test
  void thisWeekDoesNotCountPlansOfDaysNotYetCome() {
    // 이번 주는 "오늘까지" 만 봅니다. 수요일(10/07)에는 목요일 이후의 계획을 넣지 않습니다.
    // 넣으면 주초마다 "계획 대비 실제" 가 낮게 보입니다.
    List<FactPlan> plans =
        List.of(plan(1, october(5), 60), plan(2, october(7), 60), plan(3, october(9), 300));
    List<FactSession> sessions = List.of(study(10, 5, 60), study(10, 7, 60));

    WeeklyFacts weekly = WeeklyFacts.of(plans, sessions, MONDAY, october(7));

    // 계획 120분(월·수) 중 120분 → 100%. 금요일의 300분 계획은 아직 세지 않습니다.
    assertThat(weekly.thisWeek().plannedMinutes()).isEqualTo(120);
    assertThat(weekly.thisWeek().planRate()).isEqualTo(new FactValue(100, 2));
  }

  @Test
  void pastWeekCountsAllSevenDays() {
    // 지난 주를 볼 때는 월요일부터 일요일까지 7일을 모두 보고, 하루 평균도 7일로 나눕니다.
    // 오늘이 10월 14일일 때 10/05 주: 월 70분 + 일 140분 = 210분 → 하루 평균 30분
    List<FactSession> sessions = List.of(study(10, 5, 70), study(10, 11, 140), study(10, 12, 500));

    WeeklyFacts weekly = WeeklyFacts.of(List.of(), sessions, MONDAY, october(14));

    assertThat(weekly.thisWeek().studyMinutes()).isEqualTo(210);
    assertThat(weekly.dailyAverageMinutes()).isEqualTo(30);
  }

  @Test
  void lastWeekIsAlwaysTheFullSevenDaysBefore() {
    // 비교하는 "그 전 주" 는 언제나 7일(09/28 ~ 10/04)입니다. 09/27(그 앞 주)과 10/05(이번 주)는 넣지 않습니다.
    List<FactSession> sessions =
        List.of(study(9, 27, 500), study(9, 28, 30), study(10, 4, 40), study(10, 5, 500));

    WeeklyFacts weekly = WeeklyFacts.of(List.of(), sessions, MONDAY, october(5));

    assertThat(weekly.lastWeek().studyMinutes()).isEqualTo(70);
  }

  @Test
  void dailyAverageOnMondayIsDividedByOneDay() {
    // 월요일에는 하루치뿐이라 1일로 나눕니다. 일요일에는 7일로 나눕니다.
    List<FactSession> sessions = List.of(study(10, 5, 90));

    assertThat(WeeklyFacts.of(List.of(), sessions, MONDAY, october(5)).dailyAverageMinutes())
        .isEqualTo(90);
    // 90 ÷ 7 = 12.85… → 13
    assertThat(WeeklyFacts.of(List.of(), sessions, MONDAY, october(11)).dailyAverageMinutes())
        .isEqualTo(13);
  }

  @Test
  void weekFollowsTheFourAmDayRule() {
    // 월요일(10/12) 새벽 2시에 한 공부는 아직 일요일(10/11) → 10/05 주의 기록입니다.
    FactSession lateNight = new FactSession(null, at(10, 12, 2), 40 * MINUTE, 0);

    WeeklyFacts weekly = WeeklyFacts.of(List.of(), List.of(lateNight), MONDAY, october(14));

    assertThat(weekly.thisWeek().studyMinutes()).isEqualTo(40);
  }

  @Test
  void emptyWeeksGiveZeroAndUnknown() {
    // 기록이 없는 주는 순공 0분이고, 비율은 0 이 아니라 "모름" 입니다.
    WeeklyFacts weekly = WeeklyFacts.of(List.of(), List.of(), MONDAY, october(7));

    assertThat(weekly.thisWeek().studyMinutes()).isEqualTo(0);
    assertThat(weekly.thisWeek().planRate()).isEqualTo(FactValue.UNKNOWN);
    assertThat(weekly.lastWeek().completionRate()).isEqualTo(FactValue.UNKNOWN);
    assertThat(weekly.dailyAverageMinutes()).isEqualTo(0);
  }

  // ─── deltaOf: 두 주의 차이 ───

  @Test
  void feExampleDelta() {
    // FE 예) 64 와 56 → 8   /   18 과 23 → -5
    assertThat(WeeklyFacts.deltaOf(new FactValue(64, 12), new FactValue(56, 10))).isEqualTo(8);
    assertThat(WeeklyFacts.deltaOf(new FactValue(18, 4), new FactValue(23, 5))).isEqualTo(-5);
    // FE 예) 64 와 64 → null   (변화 없음)
    assertThat(WeeklyFacts.deltaOf(new FactValue(64, 12), new FactValue(64, 9))).isNull();
    // FE 예) 64 와 모름 → null   (지난주 값을 모름)
    assertThat(WeeklyFacts.deltaOf(new FactValue(64, 12), FactValue.UNKNOWN)).isNull();
  }

  @Test
  void deltaIsNullWhenThisWeekIsUnknown() {
    // 이번 주 값을 모를 때도, 둘 다 모를 때도 비교할 수 없습니다.
    assertThat(WeeklyFacts.deltaOf(FactValue.UNKNOWN, new FactValue(56, 10))).isNull();
    assertThat(WeeklyFacts.deltaOf(FactValue.UNKNOWN, FactValue.UNKNOWN)).isNull();
  }

  @Test
  void zeroIsANumberForDelta() {
    // 0% 는 "모름" 이 아니라 숫자입니다. 0 에서 30 으로 오르면 30, 30 에서 0 으로 내리면 -30 입니다.
    assertThat(WeeklyFacts.deltaOf(new FactValue(30, 3), new FactValue(0, 3))).isEqualTo(30);
    assertThat(WeeklyFacts.deltaOf(new FactValue(0, 3), new FactValue(30, 3))).isEqualTo(-30);
  }

  @Test
  void deltaWorksWithWeeklyFacts() {
    // 실제로 쓰는 흐름: 이번 주와 그 전 주의 계획 대비 실제를 비교합니다.
    // 그 전 주: 계획 100분 중 50분(50%) / 이번 주: 계획 100분 중 80분(80%) → +30
    List<FactPlan> plans =
        List.of(plan(1, LocalDate.of(2026, 9, 28), 100), plan(2, october(5), 100));
    List<FactSession> sessions = List.of(study(9, 28, 50), study(10, 5, 80));

    WeeklyFacts weekly = WeeklyFacts.of(plans, sessions, MONDAY, october(7));

    assertThat(WeeklyFacts.deltaOf(weekly.thisWeek().planRate(), weekly.lastWeek().planRate()))
        .isEqualTo(30);
  }
}
