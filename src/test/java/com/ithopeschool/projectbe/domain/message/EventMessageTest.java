package com.ithopeschool.projectbe.domain.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.message.EventMessage.Input;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// "지금 보낼 이벤트 메시지" 규칙(이슈 #31)을 확인합니다.
// feExample 로 시작하는 10개는 FE 함수(getEventMessage) 주석의 예시를 그대로 옮긴 것입니다.
class EventMessageTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 한국 시간 "시:분" 을 순간(Instant)으로 바꾸는 도우미입니다. 날짜는 2026년 10월 12일로 고정합니다.
  private static Instant seoul(int hour, int minute) {
    return ZonedDateTime.of(2026, 10, 12, hour, minute, 0, 0, SEOUL).toInstant();
  }

  // 테스트에서 쓰는 "상황" 입니다. FE 예시의 공통 조건을 기본값으로 넣어 두었습니다.
  //   지금 오후 8시, 연락 금지 23시~7시, 하루 3개, 오늘 아직 아무것도 안 보냄
  // 테스트마다 바꾸고 싶은 값만 고친 뒤 pick() 을 부릅니다.
  private static final class Situation {
    Instant now = seoul(20, 0);
    LocalTime quietStart = LocalTime.of(23, 0);
    LocalTime quietEnd = LocalTime.of(7, 0);
    int dailyCount = 3;
    List<EventMessageType> alreadySentToday = List.of();
    LocalTime plannedStartTime = null;
    boolean isTimerRunning = false;
    boolean hasStudiedToday = false;
    int todayStudyMinutes = 0;
    int todayTargetMinutes = 0;
    int consecutiveMissDays = 0;

    EventMessageType pick() {
      return EventMessage.pick(
          new Input(
              now,
              quietStart,
              quietEnd,
              dailyCount,
              alreadySentToday,
              plannedStartTime,
              isTimerRunning,
              hasStudiedToday,
              todayStudyMinutes,
              todayTargetMinutes,
              consecutiveMissDays));
    }
  }

  // FE 예1 의 상황: 목표 120분, 순공 130분 (목표 달성)
  private static Situation goalDone() {
    Situation situation = new Situation();
    situation.todayTargetMinutes = 120;
    situation.todayStudyMinutes = 130;
    situation.hasStudiedToday = true;
    return situation;
  }

  // FE 예3 의 상황: 목표 120분, 순공 0분, 3일 연속 미달
  private static Situation missedThreeDays() {
    Situation situation = new Situation();
    situation.todayTargetMinutes = 120;
    situation.todayStudyMinutes = 0;
    situation.consecutiveMissDays = 3;
    return situation;
  }

  // FE 예5 의 상황: 계획 시각 19:00, 공부 안 함, 타이머 꺼짐, 2일 연속 미달
  private static Situation planLate() {
    Situation situation = new Situation();
    situation.plannedStartTime = LocalTime.of(19, 0);
    situation.todayTargetMinutes = 120;
    situation.consecutiveMissDays = 2;
    return situation;
  }

  // ─── FE 예시 10개 ───

  @Test
  void feExample1GoalDone() {
    // FE 예1) 목표 120분, 순공 130분 → goalDone
    assertThat(goalDone().pick()).isEqualTo(EventMessageType.GOAL_DONE);
  }

  @Test
  void feExample2GoalDoneIsSentOnlyOnce() {
    // FE 예2) 목표 120분, 순공 130분, 이미 goalDone 보냄 → null (같은 메시지는 두 번 안 보냄)
    Situation situation = goalDone();
    situation.alreadySentToday = List.of(EventMessageType.GOAL_DONE);

    assertThat(situation.pick()).isNull();
  }

  @Test
  void feExample3MissedThreeDays() {
    // FE 예3) 목표 120분, 순공 0분, 3일 연속 미달 → missed3Days
    assertThat(missedThreeDays().pick()).isEqualTo(EventMessageType.MISSED_3_DAYS);
  }

  @Test
  void feExample4NoNaggingDuringRecovery() {
    // FE 예4) 예3 인데 이미 missed3Days 보냄 → null (회복 중에는 독촉하지 않음)
    Situation situation = missedThreeDays();
    situation.alreadySentToday = List.of(EventMessageType.MISSED_3_DAYS);
    // 계획 시각이 지났어도 PLAN_LATE 로 넘어가지 않습니다.
    situation.plannedStartTime = LocalTime.of(19, 0);

    assertThat(situation.pick()).isNull();
  }

  @Test
  void feExample5PlanLate() {
    // FE 예5) 계획 시각 19:00, 공부 안 함, 타이머 꺼짐, 2일 연속 미달 → planLate
    assertThat(planLate().pick()).isEqualTo(EventMessageType.PLAN_LATE);
  }

  @Test
  void feExample6NoPlanLateWhileTimerIsRunning() {
    // FE 예6) 예5 인데 타이머가 켜져 있음 → null
    Situation situation = planLate();
    situation.isTimerRunning = true;

    assertThat(situation.pick()).isNull();
  }

  @Test
  void feExample7NoPlanLateAfterStudyingToday() {
    // FE 예7) 예5 인데 오늘 이미 공부를 했음 → null
    Situation situation = planLate();
    situation.hasStudiedToday = true;

    assertThat(situation.pick()).isNull();
  }

  @Test
  void feExample8NothingIsSentDuringQuietHours() {
    // FE 예8) 지금 밤 11시 30분 (연락 금지) → null (예1~7 어느 것이든)
    for (Situation situation : List.of(goalDone(), missedThreeDays(), planLate())) {
      situation.now = seoul(23, 30);

      assertThat(situation.pick()).isNull();
    }
  }

  @Test
  void feExample9NothingIsSentWhenDailyCountIsZero() {
    // FE 예9) 하루 메시지 수 0 → null
    for (Situation situation : List.of(goalDone(), missedThreeDays(), planLate())) {
      situation.dailyCount = 0;

      assertThat(situation.pick()).isNull();
    }
  }

  @Test
  void feExample10NoPlannedTimeMeansNoPlanLate() {
    // FE 예10) 계획 시각을 정하지 않았음(null), 공부 안 함 → null (3일 연속 미달이 아니면)
    Situation situation = planLate();
    situation.plannedStartTime = null;

    assertThat(situation.pick()).isNull();
  }

  // ─── 순서: 달성 → 회복 → 계획 시간 ───

  @Test
  void goalDoneComesBeforeMissedThreeDays() {
    // 3일 연속 미달이었어도 오늘 목표를 채웠으면 칭찬이 먼저입니다.
    Situation situation = goalDone();
    situation.consecutiveMissDays = 5;

    assertThat(situation.pick()).isEqualTo(EventMessageType.GOAL_DONE);
  }

  @Test
  void goalDoneAlreadySentDoesNotFallThrough() {
    // 목표를 채운 날 칭찬을 이미 보냈으면, 다른 메시지(회복·계획 시간)로 넘어가지 않고 null 입니다.
    Situation situation = goalDone();
    situation.alreadySentToday = List.of(EventMessageType.GOAL_DONE);
    situation.consecutiveMissDays = 5;
    situation.plannedStartTime = LocalTime.of(19, 0);
    situation.hasStudiedToday = false;

    assertThat(situation.pick()).isNull();
  }

  @Test
  void missedThreeDaysComesBeforePlanLate() {
    // 3일 연속 미달이면 계획 시간이 지났어도 독촉(PLAN_LATE) 대신 회복 메시지를 보냅니다.
    Situation situation = planLate();
    situation.consecutiveMissDays = 3;

    assertThat(situation.pick()).isEqualTo(EventMessageType.MISSED_3_DAYS);
  }

  @Test
  void twoMissedDaysAreNotRecoveryYet() {
    // 경계값: 2일 연속은 아직 회복이 아니고, 3일부터입니다.
    Situation twoDays = missedThreeDays();
    twoDays.consecutiveMissDays = 2;

    assertThat(twoDays.pick()).isNull();
    assertThat(missedThreeDays().pick()).isEqualTo(EventMessageType.MISSED_3_DAYS);
  }

  // ─── 목표 달성의 경계 ───

  @Test
  void goalIsDoneWhenStudyReachesTarget() {
    // 경계값: 순공이 목표와 같으면 달성, 1분 모자라면 아직입니다.
    Situation exact = goalDone();
    exact.todayStudyMinutes = 120;
    Situation oneMinuteShort = goalDone();
    oneMinuteShort.todayStudyMinutes = 119;

    assertThat(exact.pick()).isEqualTo(EventMessageType.GOAL_DONE);
    assertThat(oneMinuteShort.pick()).isNull();
  }

  @Test
  void noGoalMeansNoGoalDone() {
    // 계획이 없어서 목표가 0분이면, 공부를 했어도 "목표 달성" 은 보내지 않습니다.
    Situation situation = new Situation();
    situation.todayTargetMinutes = 0;
    situation.todayStudyMinutes = 30;
    situation.hasStudiedToday = true;

    assertThat(situation.pick()).isNull();
  }

  // ─── 계획 시간의 경계 ───

  @Test
  void planLateStartsExactlyAtThePlannedTime() {
    // 경계값: 계획 시각 19:00 이면 18:59 는 아직, 19:00 부터 보냅니다.
    Situation before = planLate();
    before.now = seoul(18, 59);
    Situation exact = planLate();
    exact.now = seoul(19, 0);

    assertThat(before.pick()).isNull();
    assertThat(exact.pick()).isEqualTo(EventMessageType.PLAN_LATE);
  }

  @Test
  void planLateIsSentOnlyOnce() {
    // 오늘 이미 보냈으면 다시 보내지 않습니다.
    Situation situation = planLate();
    situation.alreadySentToday = List.of(EventMessageType.PLAN_LATE);

    assertThat(situation.pick()).isNull();
  }

  @Test
  void otherMessagesSentTodayDoNotBlock() {
    // 다른 종류를 이미 보낸 것은 상관없습니다. 종류마다 하루에 한 번입니다.
    Situation situation = goalDone();
    situation.alreadySentToday = List.of(EventMessageType.PLAN_LATE);

    assertThat(situation.pick()).isEqualTo(EventMessageType.GOAL_DONE);
  }

  // ─── 연락 금지 시간 ───

  @Test
  void quietHoursFollowTheUsersSetting() {
    // 금지 시간을 20:00~08:00 으로 정한 사람에게는 오후 8시에 아무것도 보내지 않습니다.
    Situation situation = goalDone();
    situation.quietStart = LocalTime.of(20, 0);
    situation.quietEnd = LocalTime.of(8, 0);

    assertThat(situation.pick()).isNull();
  }

  @Test
  void messagesGoOutAgainWhenQuietHoursEnd() {
    // 기본 금지 시간(23:00~07:00): 06:59 까지는 안 보내고, 07:00 부터 보냅니다.
    Situation stillQuiet = missedThreeDays();
    stillQuiet.now = seoul(6, 59);
    Situation morning = missedThreeDays();
    morning.now = seoul(7, 0);

    assertThat(stillQuiet.pick()).isNull();
    assertThat(morning.pick()).isEqualTo(EventMessageType.MISSED_3_DAYS);
  }

  // ─── 종류마다 붙는 값 ───

  @Test
  void eachTypeHasTheSameLabelAndCareLevelAsFe() {
    // FE 의 eventMessageLabels · eventMessageCareLevels 와 같은 값입니다.
    assertThat(EventMessageType.PLAN_LATE.getLabel()).isEqualTo("계획 시간 지남");
    assertThat(EventMessageType.GOAL_DONE.getLabel()).isEqualTo("목표 달성");
    assertThat(EventMessageType.MISSED_3_DAYS.getLabel()).isEqualTo("3일 연속 미달");

    assertThat(EventMessageType.PLAN_LATE.getCareLevel()).isEqualTo(2);
    assertThat(EventMessageType.GOAL_DONE.getCareLevel()).isEqualTo(1);
    // 3일 연속 미달은 채찍이 아니라 회복(0)입니다.
    assertThat(EventMessageType.MISSED_3_DAYS.getCareLevel()).isEqualTo(0);
  }
}
