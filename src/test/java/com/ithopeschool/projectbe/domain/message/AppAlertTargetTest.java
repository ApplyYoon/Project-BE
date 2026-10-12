package com.ithopeschool.projectbe.domain.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.message.AppAlertTarget.FirstPlan;
import com.ithopeschool.projectbe.domain.message.AppAlertTarget.PlanItem;
import com.ithopeschool.projectbe.domain.message.AppAlertTarget.TimerItem;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 앱 알림이 말할 계획과 타이머를 고르는 규칙(이슈 #31)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/appAlert.ts) 주석의 예시를 그대로 옮긴 것입니다.
// 오늘은 2026-10-12 입니다.
class AppAlertTargetTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

  private static final long MINUTE = 60_000;

  // 과목 번호
  private static final Long KOREAN = 1L;
  private static final Long MATH = 2L;

  // 한국 시간 10월 "day일 시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
  }

  // 오늘의 계획을 만듭니다. hour 가 -1 이면 시작 시각을 정하지 않은 계획입니다.
  private static PlanItem plan(long id, int hour, int minute, boolean done) {
    LocalTime startTime = hour < 0 ? null : LocalTime.of(hour, minute);
    return new PlanItem(id, TODAY, startTime, done);
  }

  // 10월 day일 hour:minute 에 시작해서 studyMinutes 분 공부한 기록을 만듭니다. (일시정지 없음)
  private static TimerItem timer(
      Long subjectId, int day, int hour, int minute, int studyMinutes, int target) {
    Instant startedAt = at(day, hour, minute);
    return new TimerItem(
        subjectId,
        startedAt,
        startedAt.plusMillis(studyMinutes * MINUTE),
        studyMinutes * MINUTE,
        target);
  }

  // ─── 가장 먼저 시작하기로 한 계획 ───

  @Test
  void feExampleFirstUnfinishedPlanWithStartTime() {
    // FE 예) 19:00 국어(안 끝냄) · 21:00 수학(안 끝냄) · 영어(시각 없음) → 19:00 국어
    PlanItem korean = plan(1, 19, 0, false);
    List<PlanItem> plans = List.of(plan(2, 21, 0, false), korean, plan(3, -1, 0, false));

    assertThat(AppAlertTarget.firstPlannedPlan(plans))
        .isEqualTo(new FirstPlan(korean, at(12, 19, 0)));
  }

  @Test
  void feExampleFinishedPlanIsSkipped() {
    // FE 예) 19:00 국어(끝냄) · 21:00 수학(안 끝냄) → 21:00 수학
    PlanItem math = plan(2, 21, 0, false);

    assertThat(AppAlertTarget.firstPlannedPlan(List.of(plan(1, 19, 0, true), math)))
        .isEqualTo(new FirstPlan(math, at(12, 21, 0)));
  }

  @Test
  void feExampleNoPlanWithStartTime() {
    // FE 예) 전부 시각 없음 → null
    assertThat(
            AppAlertTarget.firstPlannedPlan(List.of(plan(1, -1, 0, false), plan(2, -1, 0, false))))
        .isNull();
    assertThat(AppAlertTarget.firstPlannedPlan(List.of())).isNull();
  }

  @Test
  void planBeforeFourAmIsAfterLateNightPlan() {
    // FE 예) '01:00' 계획은 '23:00' 계획보다 뒤입니다. (01:00 은 달력으로 다음 날 새벽)
    PlanItem lateNight = plan(2, 23, 0, false);
    PlanItem afterMidnight = plan(1, 1, 0, false);

    assertThat(AppAlertTarget.firstPlannedPlan(List.of(afterMidnight, lateNight)))
        .isEqualTo(new FirstPlan(lateNight, at(12, 23, 0)));
    // 01:00 계획만 있으면 그 계획이고, 시작하기로 한 순간은 10월 13일 01:00 입니다.
    assertThat(AppAlertTarget.firstPlannedPlan(List.of(afterMidnight)))
        .isEqualTo(new FirstPlan(afterMidnight, at(13, 1, 0)));
  }

  @Test
  void firstInTheListWinsWhenStartTimesAreEqual() {
    PlanItem first = plan(7, 19, 0, false);
    PlanItem second = plan(3, 19, 0, false);

    assertThat(AppAlertTarget.firstPlannedPlan(List.of(first, second)).plan()).isEqualTo(first);
  }

  @Test
  void allPlansFinishedMeansNothingToRemind() {
    assertThat(AppAlertTarget.firstPlannedPlan(List.of(plan(1, 19, 0, true), plan(2, 21, 0, true))))
        .isNull();
  }

  // ─── 계획 시각이 정말 지났는지 ───

  @Test
  void startTimeIsGivenOnlyAfterItHasPassed() {
    FirstPlan first = AppAlertTarget.firstPlannedPlan(List.of(plan(1, 19, 0, false)));

    // 18:59 에는 아직 안 됐고, 19:00 이 되면 바로 지난 것으로 봅니다. (여유 시간 없음)
    assertThat(AppAlertTarget.passedStartTime(first, at(12, 18, 59))).isNull();
    assertThat(AppAlertTarget.passedStartTime(first, at(12, 19, 0))).isEqualTo(LocalTime.of(19, 0));
    assertThat(AppAlertTarget.passedStartTime(first, at(12, 20, 0))).isEqualTo(LocalTime.of(19, 0));
  }

  @Test
  void planAfterMidnightHasNotPassedInTheEvening() {
    // 저녁 8시에 새벽 1시 계획을 보면, 시각만으로는(20:00 > 01:00) 지난 것 같지만 아직 안 됐습니다.
    FirstPlan first = AppAlertTarget.firstPlannedPlan(List.of(plan(1, 1, 0, false)));

    assertThat(AppAlertTarget.passedStartTime(first, at(12, 20, 0))).isNull();
    // 다음 날 새벽 1시 30분이 되면 지난 것입니다.
    assertThat(AppAlertTarget.passedStartTime(first, at(13, 1, 30))).isEqualTo(LocalTime.of(1, 0));
  }

  @Test
  void noPlanMeansNoStartTime() {
    assertThat(AppAlertTarget.passedStartTime(null, at(12, 20, 0))).isNull();
  }

  // ─── 오늘 목표를 채운 타이머 ───

  @Test
  void feExampleTimerThatReachedItsTarget() {
    // FE 예) 오늘 기록: 목표 50분에 50분 함, 목표 30분에 10분 함 → 앞의 기록
    TimerItem finished = timer(KOREAN, 12, 19, 0, 50, 50);
    List<TimerItem> sessions = List.of(finished, timer(MATH, 12, 21, 0, 10, 30));

    assertThat(AppAlertTarget.finishedTimerToday(sessions, TODAY)).isEqualTo(finished);
  }

  @Test
  void feExampleTimerWithoutTargetIsNotFinished() {
    // FE 예) 오늘 기록: 목표 없이 2시간 함 → null (약속한 목표가 없습니다)
    assertThat(AppAlertTarget.finishedTimerToday(List.of(timer(KOREAN, 12, 19, 0, 120, 0)), TODAY))
        .isNull();
  }

  @Test
  void feExampleOnlyYesterdaysRecords() {
    // FE 예) 어제 기록만 있음 → null
    assertThat(AppAlertTarget.finishedTimerToday(List.of(timer(KOREAN, 11, 19, 0, 50, 50)), TODAY))
        .isNull();
    assertThat(AppAlertTarget.finishedTimerToday(List.of(), TODAY)).isNull();
  }

  @Test
  void latestFinishedTimerIsPicked() {
    // 목표를 채운 타이머가 둘이면 더 늦게 끝난 쪽입니다. 목록의 순서와는 상관없습니다.
    TimerItem evening = timer(MATH, 12, 21, 0, 30, 25);
    List<TimerItem> sessions = List.of(evening, timer(KOREAN, 12, 9, 0, 50, 50));

    assertThat(AppAlertTarget.finishedTimerToday(sessions, TODAY)).isEqualTo(evening);
  }

  @Test
  void finishedMeansStudyTimeReachedTheTarget() {
    // 시작부터 끝까지는 60분이지만 일시정지를 빼면 49분 59초라서, 목표 50분을 채우지 못했습니다.
    Instant startedAt = at(12, 19, 0);
    TimerItem almost =
        new TimerItem(KOREAN, startedAt, startedAt.plusMillis(60 * MINUTE), 50 * MINUTE - 1000, 50);
    // 정확히 50분이면 채운 것입니다.
    TimerItem exact =
        new TimerItem(KOREAN, startedAt, startedAt.plusMillis(60 * MINUTE), 50 * MINUTE, 50);

    assertThat(AppAlertTarget.finishedTimerToday(List.of(almost), TODAY)).isNull();
    assertThat(AppAlertTarget.finishedTimerToday(List.of(exact), TODAY)).isEqualTo(exact);
  }

  @Test
  void todayFollowsTheFourAmDayRule() {
    // 10월 13일 새벽 1시에 시작한 타이머는 아직 10월 12일(오늘) 기록입니다.
    TimerItem afterMidnight = timer(KOREAN, 13, 1, 0, 30, 30);
    // 10월 12일 새벽 3시에 시작한 타이머는 10월 11일(어제) 기록입니다.
    TimerItem beforeFourAm = timer(MATH, 12, 3, 0, 30, 30);

    assertThat(AppAlertTarget.finishedTimerToday(List.of(beforeFourAm, afterMidnight), TODAY))
        .isEqualTo(afterMidnight);
    assertThat(AppAlertTarget.finishedTimerToday(List.of(beforeFourAm), TODAY)).isNull();
  }
}
