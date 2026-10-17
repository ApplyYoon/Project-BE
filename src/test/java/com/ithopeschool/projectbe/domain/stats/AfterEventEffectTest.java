package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.stats.AfterEventEffect.Event;
import com.ithopeschool.projectbe.domain.stats.AfterEventEffect.EventEffect;
import com.ithopeschool.projectbe.domain.stats.AfterEventEffect.EventType;
import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency.Day;
import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency.DayGroup;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 이벤트 다음 날 효과(이슈 #37)의 규칙을 확인합니다.
// FE 에 같은 함수가 없어서, 기대값은 주석에 적은 대로 손으로 계산한 값입니다.
class AfterEventEffectTest {

  private static final LocalDate FROM = october(1);
  private static final LocalDate TO = october(31);

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  private static Day day(int day, int studyMinutes, Integer rate) {
    return new Day(october(day), studyMinutes, rate);
  }

  private static Event exam(int day) {
    return new Event(october(day), EventType.EXAM);
  }

  private static Event appointment(int day) {
    return new Event(october(day), EventType.APPOINTMENT);
  }

  // 10월 from 일부터 to 일까지 매일 60분 공부, 달성률 100%
  private static List<Day> usualDays(int from, int to) {
    List<Day> days = new ArrayList<>();
    for (int d = from; d <= to; d++) {
      days.add(day(d, 60, 100));
    }
    return days;
  }

  private static EventEffect effectOf(AfterEventEffect result, EventType type) {
    return result.byEvent().stream().filter(e -> e.type() == type).findFirst().orElseThrow();
  }

  @Test
  void comparesDayAfterExamWithUsualDays() {
    // 평소 10/1 ~ 10/5 매일 60분·100%
    // 시험 10/10, 10/12 (그날은 30분·50%), 다음 날 10/11, 10/13 은 20분·33%
    // → 시험 다음 날 20분·33%, 평소 60분·100%, 차이 −40분·−67%p, 다음 날이 2일이라 가설
    List<Day> days = usualDays(1, 5);
    days.add(day(10, 30, 50));
    days.add(day(11, 20, 33));
    days.add(day(12, 30, 50));
    days.add(day(13, 20, 33));

    AfterEventEffect result = AfterEventEffect.of(days, List.of(exam(10), exam(12)), FROM, TO);

    assertThat(result.usual())
        .isEqualTo(
            new DayGroup(
                60,
                100,
                List.of(october(1), october(2), october(3), october(4), october(5)),
                false));
    EventEffect examEffect = effectOf(result, EventType.EXAM);
    assertThat(examEffect.nextDays().averageStudyMinutes()).isEqualTo(20);
    assertThat(examEffect.nextDays().averageAchievementRate()).isEqualTo(33);
    assertThat(examEffect.nextDays().dates()).containsExactly(october(11), october(13));
    assertThat(examEffect.studyMinutesDiff()).isEqualTo(-40);
    assertThat(examEffect.achievementRateDiff()).isEqualTo(-67);
    assertThat(examEffect.hypothesis()).isTrue();
  }

  @Test
  void resultsComeInExamThenAppointmentOrder() {
    AfterEventEffect result = AfterEventEffect.of(List.of(), List.of(), FROM, TO);

    assertThat(result.byEvent())
        .extracting(EventEffect::type)
        .containsExactly(EventType.EXAM, EventType.APPOINTMENT);
  }

  @Test
  void typeWithoutEventsHasNullDiff() {
    // 시험만 있고 약속·행사는 없음 → 약속·행사 쪽은 다음 날이 없어서 차이가 null, 가설
    AfterEventEffect result = AfterEventEffect.of(usualDays(1, 6), List.of(exam(5)), FROM, TO);

    EventEffect appointmentEffect = effectOf(result, EventType.APPOINTMENT);
    assertThat(appointmentEffect.nextDays().dates()).isEmpty();
    assertThat(appointmentEffect.studyMinutesDiff()).isNull();
    assertThat(appointmentEffect.achievementRateDiff()).isNull();
    assertThat(appointmentEffect.hypothesis()).isTrue();
  }

  @Test
  void nextDayThatWasNotUsedIsSkipped() {
    // 시험 10/10, 10/11 은 계획도 공부도 없음 → 비교할 다음 날이 없음
    List<Day> days = usualDays(1, 5);
    days.add(day(11, 0, null));

    EventEffect examEffect =
        effectOf(AfterEventEffect.of(days, List.of(exam(10)), FROM, TO), EventType.EXAM);

    assertThat(examEffect.nextDays().dates()).isEmpty();
    assertThat(examEffect.studyMinutesDiff()).isNull();
  }

  @Test
  void dayAfterPlanWithoutStudyCountsAsZero() {
    // 약속 10/7, 10/8 은 계획만 있고 공부 0분(0%) → 다음 날로 셉니다
    List<Day> days = usualDays(1, 5);
    days.add(day(8, 0, 0));

    EventEffect appointmentEffect =
        effectOf(
            AfterEventEffect.of(days, List.of(appointment(7)), FROM, TO), EventType.APPOINTMENT);

    assertThat(appointmentEffect.nextDays().averageStudyMinutes()).isZero();
    assertThat(appointmentEffect.studyMinutesDiff()).isEqualTo(-60);
    assertThat(appointmentEffect.achievementRateDiff()).isEqualTo(-100);
  }

  @Test
  void sameDayEventsCountTheNextDayOnce() {
    // 같은 날(10/10) 체크인과 타이머 둘 다 '약속·행사' → 다음 날 10/11 은 하루
    List<Day> days = usualDays(1, 5);
    days.add(day(11, 30, 50));

    EventEffect appointmentEffect =
        effectOf(
            AfterEventEffect.of(days, List.of(appointment(10), appointment(10)), FROM, TO),
            EventType.APPOINTMENT);

    assertThat(appointmentEffect.nextDays().dates()).containsExactly(october(11));
  }

  @Test
  void eventDaysAndNextDaysOfAnyTypeAreNotUsual() {
    // 10/1 ~ 10/10 매일 60분. 약속 10/3 → 10/3, 10/4 는 평소가 아님. 시험 10/7 → 10/7, 10/8 도 평소가 아님
    AfterEventEffect result =
        AfterEventEffect.of(usualDays(1, 10), List.of(appointment(3), exam(7)), FROM, TO);

    assertThat(result.usual().dates())
        .containsExactly(october(1), october(2), october(5), october(6), october(9), october(10));
  }

  @Test
  void notHypothesisWhenBothSidesHaveFiveDays() {
    // 평소 10/1 ~ 10/5 (5일), 시험 다음 날 5번 (10/11, 13, 15, 17, 19)
    List<Day> days = usualDays(1, 5);
    List<Event> exams = new ArrayList<>();
    for (int d = 10; d <= 18; d += 2) {
      exams.add(exam(d));
      days.add(day(d + 1, 30, 50));
    }

    EventEffect examEffect = effectOf(AfterEventEffect.of(days, exams, FROM, TO), EventType.EXAM);

    assertThat(examEffect.nextDays().dates()).hasSize(5);
    assertThat(examEffect.hypothesis()).isFalse();
  }

  @Test
  void hypothesisWhenUsualDaysAreFewerThanFive() {
    // 다음 날은 5일이어도 평소 날이 3일뿐이면 가설
    List<Day> days = usualDays(1, 3);
    List<Event> exams = new ArrayList<>();
    for (int d = 10; d <= 18; d += 2) {
      exams.add(exam(d));
      days.add(day(d + 1, 30, 50));
    }

    EventEffect examEffect = effectOf(AfterEventEffect.of(days, exams, FROM, TO), EventType.EXAM);

    assertThat(examEffect.nextDays().hypothesis()).isFalse();
    assertThat(examEffect.hypothesis()).isTrue();
  }

  @Test
  void eventBeforeThePeriodCountsWhenNextDayIsInside() {
    // 기간 10/5 ~ 10/31. 시험 10/4(기간 밖) → 다음 날 10/5 는 기간 안이라 셉니다
    // 시험 10/31 → 다음 날 11/1 은 기간 밖
    List<Day> days = new ArrayList<>(usualDays(5, 31));
    days.add(new Day(LocalDate.of(2026, 11, 1), 10, 10));

    EventEffect examEffect =
        effectOf(
            AfterEventEffect.of(days, List.of(exam(4), exam(31)), october(5), TO), EventType.EXAM);

    assertThat(examEffect.nextDays().dates()).containsExactly(october(5));
  }
}
