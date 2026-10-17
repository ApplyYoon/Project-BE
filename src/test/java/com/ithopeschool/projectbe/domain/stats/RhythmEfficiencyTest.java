package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency.Day;
import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency.DayGroup;
import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency.Session;
import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency.TimeGroup;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.TimeOfDay;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 요일·시간대 효율(이슈 #37)의 규칙을 확인합니다.
// FE 에 같은 함수가 없어서, 기대값은 주석에 적은 대로 손으로 계산한 값입니다.
// 2026년 10월 5일은 월요일입니다. (10/10 토, 10/11 일, 10/12 월, 10/19 월)
class RhythmEfficiencyTest {

  private static final LocalDate FROM = LocalDate.of(2026, 9, 28);
  private static final LocalDate TO = LocalDate.of(2026, 10, 31);
  private static final long MINUTE = 60_000L;

  // byWeekday 의 칸 번호
  private static final int MONDAY = 0;
  private static final int TUESDAY = 1;
  private static final int WEDNESDAY = 2;

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 10월 day 일 hour:minute (한국 시간)
  private static Instant kst(int day, int hour, int minute) {
    return LocalDateTime.of(2026, 10, day, hour, minute)
        .atZone(ZoneId.of("Asia/Seoul"))
        .toInstant();
  }

  // 10월 day 일 hour 시에 시작해 minutes 분 동안 켠 기록, 그중 pausedMinutes 분은 일시정지
  private static Session session(int day, int hour, int minutes, int pausedMinutes) {
    Instant start = kst(day, hour, 0);
    return new Session(start, start.plusMillis(minutes * MINUTE), pausedMinutes * MINUTE);
  }

  private static RhythmEfficiency of(List<Day> days) {
    return RhythmEfficiency.of(days, List.of(), FROM, TO);
  }

  private static RhythmEfficiency ofSessions(Session... sessions) {
    return RhythmEfficiency.of(List.of(), List.of(sessions), FROM, TO);
  }

  private static TimeGroup timeGroup(RhythmEfficiency result, TimeOfDay timeOfDay) {
    return result.byTimeOfDay().stream()
        .filter(group -> group.timeOfDay() == timeOfDay)
        .findFirst()
        .orElseThrow();
  }

  // ─── 요일별 ───

  @Test
  void averagesStudyAndRateForEachWeekday() {
    // 월요일 3번: 60분(100%), 30분(50%), 0분(계획만 있음, 0%) → 평균 30분, 50%, 3일이라 가설
    RhythmEfficiency result =
        of(
            List.of(
                new Day(october(5), 60, 100),
                new Day(october(12), 30, 50),
                new Day(october(19), 0, 0)));

    assertThat(result.byWeekday()).hasSize(7);
    assertThat(result.byWeekday().get(MONDAY))
        .isEqualTo(new DayGroup(30, 50, List.of(october(5), october(12), october(19)), true));
  }

  @Test
  void dayWithoutPlanAndStudyIsNotCounted() {
    // 10/6(화) 계획도 공부도 없음 → "앱을 안 쓴 날" 이라 빠짐
    RhythmEfficiency result = of(List.of(new Day(october(6), 0, null)));

    assertThat(result.byWeekday().get(TUESDAY))
        .isEqualTo(new DayGroup(null, null, List.of(), true));
  }

  @Test
  void studyWithoutPlanCountsOnlyForStudyAverage() {
    // 10/7(수) 계획 없이 40분 공부, 10/14(수) 60분 계획에 30분(50%)
    // → 순공 평균 35분(2일), 달성률 평균은 계획이 있던 날만이라 50%
    RhythmEfficiency result =
        of(List.of(new Day(october(7), 40, null), new Day(october(14), 30, 50)));

    DayGroup wednesday = result.byWeekday().get(WEDNESDAY);
    assertThat(wednesday.averageStudyMinutes()).isEqualTo(35);
    assertThat(wednesday.averageAchievementRate()).isEqualTo(50);
    assertThat(wednesday.dates()).containsExactly(october(7), october(14));
  }

  @Test
  void averagesAreRoundedOnceAtTheEnd() {
    // 순공 10, 10, 11 → 31 ÷ 3 = 10.33… → 10 / 달성률 33, 33, 35 → 101 ÷ 3 = 33.66… → 34
    RhythmEfficiency result =
        of(
            List.of(
                new Day(october(5), 10, 33),
                new Day(october(12), 10, 33),
                new Day(october(19), 11, 35)));

    assertThat(result.byWeekday().get(MONDAY).averageStudyMinutes()).isEqualTo(10);
    assertThat(result.byWeekday().get(MONDAY).averageAchievementRate()).isEqualTo(34);
  }

  @Test
  void fiveDaysIsNotHypothesis() {
    // 월요일 5번 (9/28, 10/5, 10/12, 10/19, 10/26)
    List<Day> mondays = new ArrayList<>();
    mondays.add(new Day(LocalDate.of(2026, 9, 28), 60, 100));
    for (int day = 5; day <= 26; day += 7) {
      mondays.add(new Day(october(day), 60, 100));
    }

    assertThat(of(mondays).byWeekday().get(MONDAY).hypothesis()).isFalse();
  }

  // ─── 평일·주말 ───

  @Test
  void comparesWeekdayAndWeekend() {
    // 평일 5일(10/5 ~ 10/9) 매일 60분·100%, 주말 2일(10/10, 10/11) 0분·0%
    List<Day> days = new ArrayList<>();
    for (int day = 5; day <= 9; day++) {
      days.add(new Day(october(day), 60, 100));
    }
    days.add(new Day(october(10), 0, 0));
    days.add(new Day(october(11), 0, 0));

    RhythmEfficiency.Comparison comparison = of(days).weekdayVsWeekend();

    assertThat(comparison.weekday().averageStudyMinutes()).isEqualTo(60);
    assertThat(comparison.weekday().averageAchievementRate()).isEqualTo(100);
    assertThat(comparison.weekday().hypothesis()).isFalse();
    assertThat(comparison.weekend().averageStudyMinutes()).isZero();
    assertThat(comparison.weekend().dates()).containsExactly(october(10), october(11));
    // 주말 쪽이 2일뿐이라 비교 전체가 가설입니다.
    assertThat(comparison.hypothesis()).isTrue();
  }

  @Test
  void comparisonIsNotHypothesisWhenBothSidesHaveFiveDays() {
    // 평일 5일(10/5 ~ 10/9), 주말 5일(10/3, 10/4, 10/10, 10/11, 10/17)
    List<Day> days = new ArrayList<>();
    for (int day = 5; day <= 9; day++) {
      days.add(new Day(october(day), 60, 100));
    }
    for (int day : new int[] {3, 4, 10, 11, 17}) {
      days.add(new Day(october(day), 20, 40));
    }

    assertThat(of(days).weekdayVsWeekend().hypothesis()).isFalse();
  }

  // ─── 시간대별 ───

  @Test
  void averagesSessionMinutesAndPauseRateForEachTimeOfDay() {
    // 밤 기록 2개: 23시에 60분 켜고 15분 일시정지, 다음 날 23시에 40분 켜고 5분 일시정지
    // → 기록 하나 평균 순공 (45 + 35) ÷ 2 = 40분, 일시정지 비율 20 ÷ 100 = 20%
    RhythmEfficiency result = ofSessions(session(5, 23, 60, 15), session(6, 23, 40, 5));

    assertThat(timeGroup(result, TimeOfDay.NIGHT))
        .isEqualTo(
            new TimeGroup(TimeOfDay.NIGHT, 2, 40, 20, List.of(october(5), october(6)), true));
  }

  @Test
  void timeOfDayFollowsTheSameHoursAsWeeklyReport() {
    // 05시 → 아침, 11시 → 아침, 12시 → 낮, 18시 → 저녁, 22시 → 밤
    RhythmEfficiency result =
        ofSessions(
            session(5, 5, 30, 0),
            session(5, 11, 30, 0),
            session(5, 12, 30, 0),
            session(5, 18, 30, 0),
            session(5, 22, 30, 0));

    assertThat(result.byTimeOfDay())
        .extracting(TimeGroup::timeOfDay, TimeGroup::sessionCount)
        .containsExactly(
            tuple(TimeOfDay.MORNING, 2),
            tuple(TimeOfDay.AFTERNOON, 1),
            tuple(TimeOfDay.EVENING, 1),
            tuple(TimeOfDay.NIGHT, 1));
  }

  @Test
  void earlyMorningSessionIsNightOfThePreviousDay() {
    // 10/6(화) 새벽 1시 → 시간대는 밤, 날짜는 하루의 기준(새벽 4시)으로 10/5(월)
    RhythmEfficiency result = ofSessions(session(6, 1, 30, 0));

    assertThat(timeGroup(result, TimeOfDay.NIGHT).dates()).containsExactly(october(5));
  }

  @Test
  void untrustedPauseIsNotSubtracted() {
    // 일시정지가 음수이거나 기록보다 길면 믿을 수 없어서 0 으로 칩니다 → 순공 30분, 일시정지 비율 0%
    Instant start = kst(5, 19, 0);
    Instant end = start.plusMillis(30 * MINUTE);
    RhythmEfficiency result =
        ofSessions(new Session(start, end, -5 * MINUTE), new Session(start, end, 90 * MINUTE));

    TimeGroup evening = timeGroup(result, TimeOfDay.EVENING);
    assertThat(evening.averageSessionMinutes()).isEqualTo(30);
    assertThat(evening.pauseRate()).isZero();
    // 같은 날 기록 2개라 근거 날짜는 하루
    assertThat(evening.dates()).containsExactly(october(5));
  }

  @Test
  void emptyTimeOfDayHasNullValues() {
    RhythmEfficiency result = ofSessions(session(5, 19, 30, 0));

    assertThat(timeGroup(result, TimeOfDay.MORNING))
        .isEqualTo(new TimeGroup(TimeOfDay.MORNING, 0, null, null, List.of(), true));
  }

  // ─── 기간 ───

  @Test
  void daysAndSessionsOutsideThePeriodAreIgnored() {
    // 기간은 10/5 ~ 10/11. 10/12(월) 날과 기록은 빠짐
    RhythmEfficiency result =
        RhythmEfficiency.of(
            List.of(new Day(october(5), 60, 100), new Day(october(12), 0, 0)),
            List.of(session(5, 19, 30, 0), session(12, 19, 30, 0)),
            october(5),
            october(11));

    assertThat(result.byWeekday().get(MONDAY).dates()).containsExactly(october(5));
    assertThat(timeGroup(result, TimeOfDay.EVENING).sessionCount()).isEqualTo(1);
  }
}
