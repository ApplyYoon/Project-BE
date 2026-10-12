package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactPlan;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactSession;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.TimeOfDay;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 기간 지표 계산 규칙(이슈 #36)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/facts.ts) 주석의 예시를 그대로 옮긴 것입니다.
// 기간은 FE 예시와 같은 2026-10-05(월) ~ 2026-10-11(일) 입니다.
class StudyFactsTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

  private static final long MINUTE = 60_000;

  // 요일별 완주율 목록에서 쓰는 자리 번호입니다. (0번째 = 일요일)
  private static final int SUN = 0;
  private static final int MON = 1;
  private static final int TUE = 2;
  private static final int SAT = 6;

  // 한국 시간 10월 "day일 시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
  }

  // 10월 day일의 계획을 만듭니다. 시작 시각을 정하지 않은 계획입니다.
  private static FactPlan plan(long id, int day, int targetMinutes) {
    return new FactPlan(id, LocalDate.of(2026, 10, day), targetMinutes, null);
  }

  // 시작 시각이 있는 계획을 만듭니다.
  private static FactPlan plan(long id, int day, int targetMinutes, int hour, int minute) {
    return new FactPlan(id, LocalDate.of(2026, 10, day), targetMinutes, LocalTime.of(hour, minute));
  }

  // 계획 없이 켠 타이머 기록을 만듭니다. targetMinutes 가 0 이면 목표 없이 한 기록입니다.
  private static FactSession session(Instant startedAt, int studyMinutes, int targetMinutes) {
    return new FactSession(null, startedAt, studyMinutes * MINUTE, targetMinutes);
  }

  // 어느 계획으로 켠 타이머 기록을 만듭니다. (목표 없음, 순공 10분)
  private static FactSession sessionOf(long planId, Instant startedAt) {
    return new FactSession(planId, startedAt, 10 * MINUTE, 0);
  }

  private static StudyFacts facts(List<FactPlan> plans, List<FactSession> sessions) {
    return StudyFacts.of(plans, sessions, MONDAY, SUNDAY);
  }

  // ─── FE 예시 ───

  @Test
  void feExample1NothingRecorded() {
    // FE 예1) 계획도 기록도 없음 → plannedMinutes 0, studyMinutes 0, 숫자는 모두 { value: null, sampleCount: 0 }
    StudyFacts facts = facts(List.of(), List.of());

    assertThat(facts.plannedMinutes()).isEqualTo(0);
    assertThat(facts.studyMinutes()).isEqualTo(0);
    assertThat(facts.completedCount()).isEqualTo(0);
    assertThat(facts.planRate()).isEqualTo(FactValue.UNKNOWN);
    assertThat(facts.completionRate()).isEqualTo(FactValue.UNKNOWN);
    assertThat(facts.startDelayMinutes()).isEqualTo(FactValue.UNKNOWN);
    assertThat(facts.stopPoint()).isEqualTo(FactValue.UNKNOWN);
    assertThat(facts.completionByWeekday()).hasSize(7).containsOnly(FactValue.UNKNOWN);
    assertThat(facts.completionByTimeOfDay()).hasSize(4);
    assertThat(facts.completionByTimeOfDay().values()).containsOnly(FactValue.UNKNOWN);
  }

  @Test
  void feExample2PlanRate() {
    // FE 예2) 계획: 월 60분, 화 90분 (합 150분) / 기록: 월 순공 40분, 화 순공 56분 (합 96분)
    //         → planRate { value: 64, sampleCount: 2 }   (96 ÷ 150 = 0.64)
    List<FactPlan> plans = List.of(plan(1, 5, 60), plan(2, 6, 90));
    List<FactSession> sessions =
        List.of(session(at(5, 19, 0), 40, 0), session(at(6, 19, 0), 56, 0));

    StudyFacts facts = facts(plans, sessions);

    assertThat(facts.plannedMinutes()).isEqualTo(150);
    assertThat(facts.studyMinutes()).isEqualTo(96);
    assertThat(facts.planRate()).isEqualTo(new FactValue(64, 2));
  }

  @Test
  void feExample3CompletionRateAndStopPoint() {
    // FE 예3) 기록 3개: 목표 50분에 순공 50분(완주) / 목표 50분에 순공 31분(멈춤) / 목표 25분에 순공 30분(완주)
    //         → completedCount 2, completionRate { value: 66, sampleCount: 3 }   (2 ÷ 3 = 66.6… 버림)
    //         → stopPoint { value: 62, sampleCount: 1 }                        (31 ÷ 50 = 0.62)
    List<FactSession> sessions =
        List.of(
            session(at(5, 19, 0), 50, 50),
            session(at(6, 19, 0), 31, 50),
            session(at(7, 19, 0), 30, 25));

    StudyFacts facts = facts(List.of(), sessions);

    assertThat(facts.completedCount()).isEqualTo(2);
    assertThat(facts.completionRate()).isEqualTo(new FactValue(66, 3));
    assertThat(facts.stopPoint()).isEqualTo(new FactValue(62, 1));
  }

  @Test
  void feExample4StartDelay() {
    // FE 예4) 계획 A 는 19:00 인데 19:23 에 시작(23분 늦음), 계획 B 는 21:00 인데 20:50 에 시작(일찍 → 0분)
    //         → startDelayMinutes { value: 12, sampleCount: 2 }   ((23 + 0) ÷ 2 = 11.5 → 반올림 12)
    List<FactPlan> plans = List.of(plan(1, 5, 60, 19, 0), plan(2, 5, 60, 21, 0));
    List<FactSession> sessions = List.of(sessionOf(1, at(5, 19, 23)), sessionOf(2, at(5, 20, 50)));

    assertThat(facts(plans, sessions).startDelayMinutes()).isEqualTo(new FactValue(12, 2));
  }

  @Test
  void feExample5PlanWithoutAnyTimer() {
    // FE 예5) 계획은 있는데 타이머를 한 번도 안 켬: 계획 월 60분 / 기록 없음
    //         → planRate { value: 0, sampleCount: 1 }  (계획이 있었으니 "0% 했다" 는 계산할 수 있습니다)
    //         → completionRate · startDelayMinutes · stopPoint 는 { value: null, sampleCount: 0 }
    StudyFacts facts = facts(List.of(plan(1, 5, 60, 19, 0)), List.of());

    assertThat(facts.planRate()).isEqualTo(new FactValue(0, 1));
    assertThat(facts.completionRate()).isEqualTo(FactValue.UNKNOWN);
    assertThat(facts.startDelayMinutes()).isEqualTo(FactValue.UNKNOWN);
    assertThat(facts.stopPoint()).isEqualTo(FactValue.UNKNOWN);
  }

  @Test
  void feExample6OnlyStudyRecordsAreGiven() {
    // FE 예6) 계획: 공부 60분 + 놀기 30분 / 기록: 공부 순공 30분 + 놀기 30분(목표 30분)
    //         → plannedMinutes 60, studyMinutes 30, planRate { value: 50, sampleCount: 1 },
    // completionRate 는 null
    // 서버에서는 노는 계획·노는 기록을 부르는 쪽이 미리 빼고 넘깁니다. 그래서 공부 것만 넣어서 같은 결과를 확인합니다.
    StudyFacts facts = facts(List.of(plan(1, 5, 60)), List.of(session(at(5, 19, 0), 30, 0)));

    assertThat(facts.plannedMinutes()).isEqualTo(60);
    assertThat(facts.studyMinutes()).isEqualTo(30);
    assertThat(facts.planRate()).isEqualTo(new FactValue(50, 1));
    assertThat(facts.completionRate()).isEqualTo(FactValue.UNKNOWN);
  }

  @Test
  void feExample7ByWeekdayAndTimeOfDay() {
    // FE 예7) 화 19:00 에 시작해 완주, 화 23:10 에 시작해 멈춤, 토 09:00 에 시작해 완주
    //         → 요일별: [2](화) { 50, 2 }, [6](토) { 100, 1 }, 나머지 null
    //         → 시간대별: 저녁 { 100, 1 }, 밤 { 0, 1 }, 아침 { 100, 1 }, 낮 { null, 0 }
    List<FactSession> sessions =
        List.of(
            session(at(6, 19, 0), 50, 50),
            session(at(6, 23, 10), 20, 50),
            session(at(10, 9, 0), 50, 50));

    StudyFacts facts = facts(List.of(), sessions);

    assertThat(facts.completionByWeekday().get(TUE)).isEqualTo(new FactValue(50, 2));
    assertThat(facts.completionByWeekday().get(SAT)).isEqualTo(new FactValue(100, 1));
    for (int weekday : new int[] {0, 1, 3, 4, 5}) {
      assertThat(facts.completionByWeekday().get(weekday)).isEqualTo(FactValue.UNKNOWN);
    }

    assertThat(facts.completionByTimeOfDay().get(TimeOfDay.EVENING))
        .isEqualTo(new FactValue(100, 1));
    assertThat(facts.completionByTimeOfDay().get(TimeOfDay.NIGHT)).isEqualTo(new FactValue(0, 1));
    assertThat(facts.completionByTimeOfDay().get(TimeOfDay.MORNING))
        .isEqualTo(new FactValue(100, 1));
    assertThat(facts.completionByTimeOfDay().get(TimeOfDay.AFTERNOON)).isEqualTo(FactValue.UNKNOWN);
  }

  @Test
  void feExample8FourAmDayRule() {
    // FE 예8) 새벽 4시 기준: 10/12(월) 01:30 에 시작해 완주한 기록
    //         → 10/11(일)의 기록입니다. 기간(10/05 ~ 10/11) 안에 들어가고, 요일은 일요일([0]), 시간대는 밤입니다.
    StudyFacts facts = facts(List.of(), List.of(session(at(12, 1, 30), 50, 50)));

    assertThat(facts.completionRate()).isEqualTo(new FactValue(100, 1));
    assertThat(facts.completionByWeekday().get(SUN)).isEqualTo(new FactValue(100, 1));
    assertThat(facts.completionByWeekday().get(MON)).isEqualTo(FactValue.UNKNOWN);
    assertThat(facts.completionByTimeOfDay().get(TimeOfDay.NIGHT)).isEqualTo(new FactValue(100, 1));
  }

  @Test
  void feExampleTimeOfDay() {
    // FE 예) getTimeOfDay(7) → 아침, (12) → 낮, (19) → 저녁, (23) → 밤, (2) → 밤
    assertThat(StudyFacts.timeOfDay(7)).isEqualTo(TimeOfDay.MORNING);
    assertThat(StudyFacts.timeOfDay(12)).isEqualTo(TimeOfDay.AFTERNOON);
    assertThat(StudyFacts.timeOfDay(19)).isEqualTo(TimeOfDay.EVENING);
    assertThat(StudyFacts.timeOfDay(23)).isEqualTo(TimeOfDay.NIGHT);
    assertThat(StudyFacts.timeOfDay(2)).isEqualTo(TimeOfDay.NIGHT);
  }

  // ─── 기간 (규칙 1, 2) ───

  @Test
  void periodIncludesBothEnds() {
    // 시작 날짜(10/5)와 끝 날짜(10/11)의 계획은 세고, 하루 앞(10/4)·하루 뒤(10/12)는 세지 않습니다.
    List<FactPlan> plans =
        List.of(plan(1, 4, 100), plan(2, 5, 30), plan(3, 11, 60), plan(4, 12, 100));

    StudyFacts facts = facts(plans, List.of());

    assertThat(facts.plannedMinutes()).isEqualTo(90);
    assertThat(facts.planRate().sampleCount()).isEqualTo(2);
  }

  @Test
  void sessionsAreCountedByTheDayTheyStarted() {
    // 기록은 "시작한 날"(새벽 4시 기준)이 기간 안이어야 셉니다.
    // 10/5 03:59 는 아직 10/4 라서 빼고, 10/5 04:00 과 10/12 03:59(아직 10/11)는 셉니다. 10/12 04:00 은 뺍니다.
    List<FactSession> sessions =
        List.of(
            session(at(5, 3, 59), 100, 0),
            session(at(5, 4, 0), 10, 0),
            session(at(12, 3, 59), 20, 0),
            session(at(12, 4, 0), 100, 0));

    assertThat(facts(List.of(), sessions).studyMinutes()).isEqualTo(30);
  }

  // ─── 계획 대비 실제 (규칙 4) ───

  @Test
  void planRateCanGoOverHundred() {
    // 계획보다 더 한 것도 사실이라 100 에서 자르지 않습니다. 계획 60분, 순공 90분 → 150%
    StudyFacts facts = facts(List.of(plan(1, 5, 60)), List.of(session(at(5, 19, 0), 90, 0)));

    assertThat(facts.planRate()).isEqualTo(new FactValue(150, 1));
  }

  @Test
  void planRateIsUnknownWithoutPlans() {
    // 계획 없이 공부만 했으면 순공은 세지만, 계획 대비 실제는 0 이 아니라 null 입니다.
    StudyFacts facts = facts(List.of(), List.of(session(at(5, 19, 0), 90, 0)));

    assertThat(facts.studyMinutes()).isEqualTo(90);
    assertThat(facts.planRate()).isEqualTo(FactValue.UNKNOWN);
  }

  // ─── 완주율 · 멈추는 지점 (규칙 5, 6, 8) ───

  @Test
  void completedMeansStudyTimeReachedTheTarget() {
    // 경계값: 순공이 목표와 같으면 완주, 1초라도 모자라면 멈춘 것입니다.
    FactSession exact = new FactSession(null, at(5, 19, 0), 50 * MINUTE, 50);
    FactSession oneSecondShort = new FactSession(null, at(6, 19, 0), 50 * MINUTE - 1_000, 50);

    StudyFacts facts = facts(List.of(), List.of(exact, oneSecondShort));

    assertThat(facts.completedCount()).isEqualTo(1);
    assertThat(facts.completionRate()).isEqualTo(new FactValue(50, 2));
    // 49분 59초 ÷ 50분 = 99.96…% → 소수점을 버려서 99
    assertThat(facts.stopPoint()).isEqualTo(new FactValue(99, 1));
  }

  @Test
  void timersWithoutTargetAreNotCountedForCompletion() {
    // 목표 없이 켠 타이머는 완주율에 넣지 않습니다. 순공시간에는 들어갑니다.
    List<FactSession> sessions =
        List.of(session(at(5, 19, 0), 30, 0), session(at(6, 19, 0), 50, 50));

    StudyFacts facts = facts(List.of(), sessions);

    assertThat(facts.studyMinutes()).isEqualTo(80);
    assertThat(facts.completionRate()).isEqualTo(new FactValue(100, 1));
  }

  @Test
  void stopPointIsTheAverageOfStoppedTimers() {
    // 멈춘 타이머들의 평균입니다. 31 ÷ 50 = 62%, 10 ÷ 40 = 25% → (62 + 25) ÷ 2 = 43.5 → 소수점을 버려서 43
    // 완주한 기록(100%)은 평균에 넣지 않습니다.
    List<FactSession> sessions =
        List.of(
            session(at(5, 19, 0), 31, 50),
            session(at(6, 19, 0), 10, 40),
            session(at(7, 19, 0), 60, 60));

    assertThat(facts(List.of(), sessions).stopPoint()).isEqualTo(new FactValue(43, 2));
  }

  // ─── 시작 지연 (규칙 7) ───

  @Test
  void delayUsesTheFirstTimerOfThePlan() {
    // 한 계획으로 타이머를 여러 번 켰으면 가장 먼저 켠 것으로 셉니다. 19:00 계획, 19:10 과 21:00 에 시작 → 10분
    List<FactPlan> plans = List.of(plan(1, 5, 60, 19, 0));
    List<FactSession> sessions = List.of(sessionOf(1, at(5, 21, 0)), sessionOf(1, at(5, 19, 10)));

    assertThat(facts(plans, sessions).startDelayMinutes()).isEqualTo(new FactValue(10, 1));
  }

  @Test
  void plansNeverStartedAreNotInTheDelaySample() {
    // 한 번도 시작하지 않은 계획은 "늦은 것" 이 아니라 "안 한 것" 이라 표본에서 뺍니다.
    // 시작 시각을 안 정한 계획도 뺍니다.
    List<FactPlan> plans = List.of(plan(1, 5, 60, 19, 0), plan(2, 6, 60, 19, 0), plan(3, 7, 60));
    List<FactSession> sessions = List.of(sessionOf(1, at(5, 19, 30)), sessionOf(3, at(7, 22, 0)));

    assertThat(facts(plans, sessions).startDelayMinutes()).isEqualTo(new FactValue(30, 1));
  }

  @Test
  void plannedTimeBeforeFourAmIsTheNextCalendarDay() {
    // 계획의 시작 시각이 00:00 ~ 03:59 이면 달력으로는 다음 날 새벽입니다. (10/5 계획의 01:00 = 10/6 01:00)
    FactPlan lateNightPlan = plan(1, 5, 60, 1, 0);

    assertThat(StudyFacts.plannedStartAt(lateNightPlan)).isEqualTo(at(6, 1, 0));
    // 10/6 01:20 에 시작했으면 20분 늦은 것입니다. (하루 넘게 일찍 시작한 것이 아닙니다)
    assertThat(
            facts(List.of(lateNightPlan), List.of(sessionOf(1, at(6, 1, 20)))).startDelayMinutes())
        .isEqualTo(new FactValue(20, 1));

    // 04:00 부터는 그날 그대로입니다.
    assertThat(StudyFacts.plannedStartAt(plan(2, 5, 60, 4, 0))).isEqualTo(at(5, 4, 0));
    // 시작 시각을 안 정한 계획은 null 입니다.
    assertThat(StudyFacts.plannedStartAt(plan(3, 5, 60))).isNull();
  }

  @Test
  void delayLooksAtTimersOutsideThePeriodToo() {
    // 기간 마지막 날(10/11)의 23:50 계획을 기간이 끝난 뒤(10/12 04:10)에 처음 시작했어도 늦은 것으로 셉니다.
    // 그 기록은 기간 밖이라 순공시간에는 넣지 않습니다.
    List<FactPlan> plans = List.of(plan(1, 11, 60, 23, 50));
    List<FactSession> sessions = List.of(sessionOf(1, at(12, 4, 10)));

    StudyFacts facts = facts(plans, sessions);

    assertThat(facts.startDelayMinutes()).isEqualTo(new FactValue(260, 1));
    assertThat(facts.studyMinutes()).isEqualTo(0);
  }

  // ─── 요일별 · 시간대별 (규칙 9) ───

  @Test
  void weekdayFollowsTheDayRuleButTimeOfDayIsTheClock() {
    // 화요일(10/6) 새벽 1시에 시작한 기록: 요일은 "그 기록이 속한 날" 인 월요일, 시간대는 시계 그대로 밤입니다.
    StudyFacts facts = facts(List.of(), List.of(session(at(6, 1, 0), 50, 50)));

    assertThat(facts.completionByWeekday().get(MON)).isEqualTo(new FactValue(100, 1));
    assertThat(facts.completionByWeekday().get(TUE)).isEqualTo(FactValue.UNKNOWN);
    assertThat(facts.completionByTimeOfDay().get(TimeOfDay.NIGHT)).isEqualTo(new FactValue(100, 1));
  }

  @Test
  void timeOfDayBoundaries() {
    // 경계값: 아침 05:00 ~ 11:59 / 낮 12:00 ~ 17:59 / 저녁 18:00 ~ 21:59 / 밤 22:00 ~ 04:59
    assertThat(StudyFacts.timeOfDay(4)).isEqualTo(TimeOfDay.NIGHT);
    assertThat(StudyFacts.timeOfDay(5)).isEqualTo(TimeOfDay.MORNING);
    assertThat(StudyFacts.timeOfDay(11)).isEqualTo(TimeOfDay.MORNING);
    assertThat(StudyFacts.timeOfDay(17)).isEqualTo(TimeOfDay.AFTERNOON);
    assertThat(StudyFacts.timeOfDay(18)).isEqualTo(TimeOfDay.EVENING);
    assertThat(StudyFacts.timeOfDay(21)).isEqualTo(TimeOfDay.EVENING);
    assertThat(StudyFacts.timeOfDay(22)).isEqualTo(TimeOfDay.NIGHT);
    assertThat(StudyFacts.timeOfDay(0)).isEqualTo(TimeOfDay.NIGHT);
  }
}
