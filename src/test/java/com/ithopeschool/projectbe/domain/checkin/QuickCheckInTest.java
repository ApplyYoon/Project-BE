package com.ithopeschool.projectbe.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.checkin.QuickCheckIn.PlanItem;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// "1탭 체크인" 규칙(이슈 #24)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/quickCheckIn.ts) 주석의 예시를 그대로 옮긴 것입니다.
// 나머지 기대값도 FE 함수를 직접 돌려서 나온 값과 같습니다.
class QuickCheckInTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 한국 시간 10월 "day일 시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
  }

  private static PlanItem study(int day, int minutes) {
    return new PlanItem(october(day), PlanKind.STUDY, minutes);
  }

  private static PlanItem play(int day, int minutes) {
    return new PlanItem(october(day), PlanKind.PLAY, minutes);
  }

  // 10/09 [수학 45분], 10/10 [수학 60분, 영어 30분, 놀기 20분]
  private static final List<PlanItem> PLANS =
      List.of(study(9, 45), study(10, 60), study(10, 30), play(10, 20));

  // 처음 계획에서 정한 하루 목표(분)
  private static final int DAILY_GOAL = 120;

  // ─── 오늘 예상 순공시간 ───

  @Test
  void feExampleStudyPlansAreSummedWithoutPlayPlans() {
    // FE 예) [수학 60분, 영어 30분, 놀기 20분], 120 → 90 (노는 계획은 뺍니다)
    List<PlanItem> plans = List.of(study(10, 60), study(10, 30), play(10, 20));

    assertThat(QuickCheckIn.plannedMinutes(plans, 120)).isEqualTo(90);
  }

  @Test
  void feExampleNoPlansUsesTheFallback() {
    // FE 예) [], 120 → 120 (오늘 계획이 없으면 하루 목표)
    assertThat(QuickCheckIn.plannedMinutes(List.of(), 120)).isEqualTo(120);
  }

  @Test
  void onlyPlayPlansUsesTheFallback() {
    // 노는 계획만 있는 날은 공부 계획이 없는 날입니다.
    assertThat(QuickCheckIn.plannedMinutes(List.of(play(10, 20)), 120)).isEqualTo(120);
  }

  @Test
  void zeroMinutePlansUseTheFallback() {
    // 공부 계획이 있어도 목표 시간의 합이 0 이면 하루 목표를 씁니다.
    assertThat(QuickCheckIn.plannedMinutes(List.of(study(10, 0)), 120)).isEqualTo(120);
  }

  @Test
  void noPlansAndNoFallbackIsZero() {
    // 계획도 처음 계획도 없으면 0분입니다.
    assertThat(QuickCheckIn.plannedMinutes(List.of(), 0)).isEqualTo(0);
  }

  @Test
  void planWithoutKindCountsAsStudy() {
    // 종류가 비어 있는 계획은 공부 계획으로 봅니다. (FE 도 kind 가 없으면 공부로 봅니다)
    List<PlanItem> plans = List.of(new PlanItem(october(10), null, 60));

    assertThat(QuickCheckIn.plannedMinutes(plans, 120)).isEqualTo(60);
  }

  // ─── 기분 하나로 체크인 만들기 ───

  @Test
  void feExampleConditionIsFilledWithTheMood() {
    // FE 예) makeQuickCheckIn('2026-10-10', 4, 90) → 날짜 10/10, 컨디션 4, 기분 4, 예상 90분
    assertThat(QuickCheckIn.of(october(10), 4, 90))
        .isEqualTo(new QuickCheckIn(october(10), 4, 4, 90));
  }

  @Test
  void lowestMoodAlsoFillsTheCondition() {
    assertThat(QuickCheckIn.of(october(10), 1, 0))
        .isEqualTo(new QuickCheckIn(october(10), 1, 1, 0));
  }

  // ─── 지금 고른 기분으로 오늘의 체크인 만들기 ───

  @Test
  void usesTodaysStudyPlansAsTheExpectedMinutes() {
    // 10/10 09:00 에 기분 4 → 10/10 의 공부 계획 60 + 30 = 90분 (놀기 20분과 10/09 계획은 세지 않습니다)
    assertThat(QuickCheckIn.of(at(10, 9, 0), 4, PLANS, DAILY_GOAL))
        .isEqualTo(new QuickCheckIn(october(10), 4, 4, 90));
  }

  @Test
  void beforeFourAmIsThePreviousDay() {
    // 새벽 1시에 누르면 전날(10/09)의 체크인이고, 예상 시간도 10/09 계획(45분)입니다.
    assertThat(QuickCheckIn.of(at(10, 1, 0), 2, PLANS, DAILY_GOAL))
        .isEqualTo(new QuickCheckIn(october(9), 2, 2, 45));
    // 03:59 까지는 전날입니다.
    assertThat(QuickCheckIn.of(at(10, 3, 59), 2, PLANS, DAILY_GOAL))
        .isEqualTo(new QuickCheckIn(october(9), 2, 2, 45));
  }

  @Test
  void fourAmStartsTheNewDay() {
    // 04:00 부터는 그날(10/10)입니다.
    assertThat(QuickCheckIn.of(at(10, 4, 0), 2, PLANS, DAILY_GOAL))
        .isEqualTo(new QuickCheckIn(october(10), 2, 2, 90));
  }

  @Test
  void dayWithoutPlansUsesTheDailyGoal() {
    // 10/11 에는 계획이 없어서, 처음 계획에서 정한 하루 목표(120분)를 예상으로 씁니다.
    assertThat(QuickCheckIn.of(at(11, 9, 0), 3, PLANS, DAILY_GOAL))
        .isEqualTo(new QuickCheckIn(october(11), 3, 3, 120));
  }

  @Test
  void dayWithoutPlansAndWithoutDailyGoalIsZero() {
    // 계획도 없고 처음 계획도 세우지 않았으면 예상은 0분입니다.
    assertThat(QuickCheckIn.of(at(11, 9, 0), 3, PLANS, 0))
        .isEqualTo(new QuickCheckIn(october(11), 3, 3, 0));
  }
}
