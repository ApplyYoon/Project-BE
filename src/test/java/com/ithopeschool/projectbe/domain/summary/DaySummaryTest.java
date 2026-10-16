package com.ithopeschool.projectbe.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DayPlan;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 하루 요약 계산 규칙(이슈 #34)을 확인합니다.
// feExample 로 시작하는 10개는 FE 함수(getDaySummary) 주석의 예시를 그대로 옮긴 것입니다.
class DaySummaryTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 테스트에서 계산하는 날: 2026년 10월 5일
  private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
  private static final LocalDate OCT_4 = LocalDate.of(2026, 10, 4);

  private static final long MINUTE = 60_000;

  // 한국 시간 10월 "day일 시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
  }

  // 일시정지한 시간이 없는 공부 기록을 만듭니다.
  private static DaySession session(Instant startedAt, Instant endedAt, int pauseCount) {
    return new DaySession(startedAt, endedAt, 0, pauseCount);
  }

  @Test
  void feExample1NoPlansNoSessions() {
    // FE 예1) 계획도 기록도 없는 날 → 순공 0, 달성률 null, 첫 시작 null, 평균 0, 일시정지 0
    assertThat(DaySummary.of(OCT_5, List.of(), List.of()))
        .isEqualTo(new DaySummary(0, null, null, 0, 0));
  }

  @Test
  void feExample2PlanWithoutStudyIsZeroPercent() {
    // FE 예2) 계획만 있고 공부는 안 한 날 (수학 60분 계획, 기록 없음) → 달성률 0
    // 0% 는 "계획은 있는데 공부를 안 한 날" 이고, null 은 "계획이 없는 날" 입니다.
    assertThat(DaySummary.of(OCT_5, List.of(new DayPlan(OCT_5, 60)), List.of()))
        .isEqualTo(new DaySummary(0, 0, null, 0, 0));
  }

  @Test
  void feExample3ZeroMinutePlanCannotBeDivided() {
    // FE 예3) 계획 목표가 0분인데 공부한 날: 수학 0분 계획, 10:00~10:30 공부 → 순공 30, 달성률 null
    List<DaySession> sessions = List.of(session(at(5, 10, 0), at(5, 10, 30), 0));

    assertThat(DaySummary.of(OCT_5, List.of(new DayPlan(OCT_5, 0)), sessions))
        .isEqualTo(new DaySummary(30, null, at(5, 10, 0), 30, 0));
  }

  @Test
  void feExample4TwoSessionsAgainstTwoPlans() {
    // FE 예4) 계획 240분 (수학 120 + 영어 120), 공부 두 번 (10:00~11:00 일시정지 1번, 14:00~16:10 일시정지 2번)
    //         순공 60 + 130 = 190분, 190 ÷ 240 = 79.1…%, 일시정지 1 + 2 = 3번, 평균 95분
    List<DayPlan> plans = List.of(new DayPlan(OCT_5, 120), new DayPlan(OCT_5, 120));
    List<DaySession> sessions =
        List.of(session(at(5, 10, 0), at(5, 11, 0), 1), session(at(5, 14, 0), at(5, 16, 10), 2));

    assertThat(DaySummary.of(OCT_5, plans, sessions))
        .isEqualTo(new DaySummary(190, 79, at(5, 10, 0), 95, 3));
  }

  @Test
  void feExample5RateIsCappedAtHundred() {
    // FE 예5) 계획 60분인데 90분 공부 (100 을 넘는 경우) → 순공 90, 달성률 100
    List<DaySession> sessions = List.of(session(at(5, 10, 0), at(5, 11, 30), 0));

    DaySummary summary = DaySummary.of(OCT_5, List.of(new DayPlan(OCT_5, 60)), sessions);

    assertThat(summary.studyMinutes()).isEqualTo(90);
    assertThat(summary.achievementRate()).isEqualTo(100);
  }

  @Test
  void feExample6AlmostDoneIsNotRoundedUp() {
    // FE 예6) 계획 240분인데 239분 공부: 239 ÷ 240 = 99.58…% 이지만 소수점을 버려서 99
    List<DaySession> sessions = List.of(session(at(5, 10, 0), at(5, 13, 59), 0));

    DaySummary summary = DaySummary.of(OCT_5, List.of(new DayPlan(OCT_5, 240)), sessions);

    assertThat(summary.studyMinutes()).isEqualTo(239);
    assertThat(summary.achievementRate()).isEqualTo(99);
  }

  @Test
  void feExample7SessionCrossingFourAmBelongsToTheStartDay() {
    // FE 예7) 새벽 4시를 넘긴 기록 — 10월 5일 03:30 ~ 04:30 (60분, 일시정지 2번)
    //         03:30 은 아직 10월 4일이라서, 60분 전부가 10월 4일 기록입니다.
    List<DaySession> sessions = List.of(session(at(5, 3, 30), at(5, 4, 30), 2));

    assertThat(DaySummary.of(OCT_4, List.of(), sessions))
        .isEqualTo(new DaySummary(60, null, at(5, 3, 30), 60, 2));
    assertThat(DaySummary.of(OCT_5, List.of(), sessions))
        .isEqualTo(new DaySummary(0, null, null, 0, 0));
  }

  @Test
  void feExample8OtherDaysAreIgnored() {
    // FE 예8) 다른 날 기록이 섞여 있는 경우: date 가 10월 5일이면 10월 4일 계획과 기록은 모두 무시합니다.
    List<DayPlan> plans = List.of(new DayPlan(OCT_4, 300), new DayPlan(OCT_5, 60));
    List<DaySession> sessions =
        List.of(session(at(4, 20, 0), at(4, 22, 0), 5), session(at(5, 20, 0), at(5, 20, 30), 1));

    // 10월 5일: 계획 60분, 순공 30분, 일시정지 1번 (10월 4일의 5번은 세지 않습니다)
    assertThat(DaySummary.of(OCT_5, plans, sessions))
        .isEqualTo(new DaySummary(30, 50, at(5, 20, 0), 30, 1));
  }

  @Test
  void feExample9StrangePauseCountIsZero() {
    // FE 예9) 그날 기록 3개의 일시정지 횟수가 [2, 없음(옛 기록), -1] 이면 → 2
    //         서버에서는 "없음" 을 0 으로 받습니다. 음수는 0번으로 칩니다.
    List<DaySession> sessions =
        List.of(
            session(at(5, 10, 0), at(5, 10, 10), 2),
            session(at(5, 11, 0), at(5, 11, 10), 0),
            session(at(5, 12, 0), at(5, 12, 10), -1));

    assertThat(DaySummary.of(OCT_5, List.of(), sessions).pauseCount()).isEqualTo(2);
  }

  @Test
  void feExample10PausedTimeIsSubtracted() {
    // FE 예10) 계획 60분, 19:00 시작 → 19:20 일시정지 → 20:00 다시 시작 → 20:10 정지
    //          (끝 − 시작 70분, 일시정지한 시간 40분, 일시정지 1번) → 순공 30, 달성률 50
    List<DaySession> sessions =
        List.of(new DaySession(at(5, 19, 0), at(5, 20, 10), 40 * MINUTE, 1));

    assertThat(DaySummary.of(OCT_5, List.of(new DayPlan(OCT_5, 60)), sessions))
        .isEqualTo(new DaySummary(30, 50, at(5, 19, 0), 30, 1));
  }

  // ─── 하루의 기준 (규칙 1, 2) ───

  @Test
  void dayChangesAtFourAm() {
    // 경계값: 03:59 에 시작한 기록은 전날, 04:00 에 시작한 기록은 그날입니다.
    DaySession beforeFour = session(at(5, 3, 59), at(5, 4, 9), 0);
    DaySession atFour = session(at(5, 4, 0), at(5, 4, 10), 0);
    List<DaySession> sessions = List.of(beforeFour, atFour);

    assertThat(DaySummary.of(OCT_4, List.of(), sessions).firstStartAt()).isEqualTo(at(5, 3, 59));
    assertThat(DaySummary.of(OCT_5, List.of(), sessions).firstStartAt()).isEqualTo(at(5, 4, 0));
    assertThat(DaySummary.of(OCT_4, List.of(), sessions).studyMinutes()).isEqualTo(10);
    assertThat(DaySummary.of(OCT_5, List.of(), sessions).studyMinutes()).isEqualTo(10);
  }

  @Test
  void firstStartIsTheEarliestRegardlessOfListOrder() {
    // 첫 시작 시각은 목록의 순서와 상관없이 가장 이른 것입니다.
    List<DaySession> sessions =
        List.of(
            session(at(5, 14, 0), at(5, 15, 0), 0),
            session(at(5, 9, 30), at(5, 10, 0), 0),
            session(at(5, 20, 0), at(5, 21, 0), 0));

    assertThat(DaySummary.of(OCT_5, List.of(), sessions).firstStartAt()).isEqualTo(at(5, 9, 30));
  }

  // ─── 반올림 (규칙 3, 4) ───

  @Test
  void studyMinutesAreRoundedOnlyOnceAtTheEnd() {
    // 기록마다 반올림하지 않고, 모두 더한 뒤 한 번만 반올림합니다.
    // 20분 24초짜리 기록 3개 = 61분 12초 → 61분. (기록마다 반올림하면 20 + 20 + 20 = 60분이 됩니다)
    Instant start = at(5, 10, 0);
    long length = 20 * MINUTE + 24_000;
    List<DaySession> sessions =
        List.of(
            new DaySession(start, start.plusMillis(length), 0, 0),
            new DaySession(
                start.plusSeconds(3600), start.plusSeconds(3600).plusMillis(length), 0, 0),
            new DaySession(
                start.plusSeconds(7200), start.plusSeconds(7200).plusMillis(length), 0, 0));

    DaySummary summary = DaySummary.of(OCT_5, List.of(), sessions);

    assertThat(summary.studyMinutes()).isEqualTo(61);
    // 평균은 61분 12초 ÷ 3 = 20분 24초 → 20분
    assertThat(summary.averageSessionMinutes()).isEqualTo(20);
  }

  // ─── 잘못된 기록 (규칙 7, 9) ───

  @Test
  void sessionEndingBeforeItStartsCountsAsZeroMinutes() {
    // 끝이 시작보다 빠른 기록은 0분입니다. 그래도 기록 수에는 들어가서 평균이 내려갑니다.
    List<DaySession> sessions =
        List.of(session(at(5, 10, 0), at(5, 11, 0), 0), session(at(5, 12, 0), at(5, 11, 30), 0));

    DaySummary summary = DaySummary.of(OCT_5, List.of(), sessions);

    assertThat(summary.studyMinutes()).isEqualTo(60);
    assertThat(summary.averageSessionMinutes()).isEqualTo(30);
  }

  @Test
  void strangePausedTimeIsNotSubtracted() {
    // 일시정지한 시간이 음수이거나 (끝 − 시작)보다 크면 믿을 수 없는 값이라 빼지 않습니다.
    DaySession negativePause = new DaySession(at(5, 10, 0), at(5, 10, 50), -5 * MINUTE, 0);
    DaySession tooLongPause = new DaySession(at(5, 12, 0), at(5, 12, 50), 90 * MINUTE, 0);

    assertThat(DaySummary.of(OCT_5, List.of(), List.of(negativePause)).studyMinutes())
        .isEqualTo(50);
    assertThat(DaySummary.of(OCT_5, List.of(), List.of(tooLongPause)).studyMinutes()).isEqualTo(50);
  }

  // ─── 달성률 ───

  @Test
  void studyWithoutPlanIsNullNotZero() {
    // 계획 없이 공부만 한 날의 달성률은 0 이 아니라 null 입니다.
    List<DaySession> sessions = List.of(session(at(5, 10, 0), at(5, 11, 0), 0));

    DaySummary summary = DaySummary.of(OCT_5, List.of(), sessions);

    assertThat(summary.studyMinutes()).isEqualTo(60);
    assertThat(summary.achievementRate()).isNull();
  }

  // ─── 노는 계획 · 노는 기록 (규칙 10) ───

  @Test
  void playPlansAndPlaySessionsAreLeftOut() {
    // 공부 계획 60분 + 노는 계획 30분, 공부 기록 45분 + 노는 기록 30분
    // → 순공 45, 달성률 45 ÷ 60 = 75 (노는 30분이 들어가면 75 ÷ 90 = 83 으로 틀리게 나옵니다)
    List<DayPlan> plans =
        List.of(new DayPlan(OCT_5, 60, PlanKind.STUDY), new DayPlan(OCT_5, 30, PlanKind.PLAY));
    List<DaySession> sessions =
        List.of(
            new DaySession(at(5, 10, 0), at(5, 10, 45), 0, 1, PlanKind.STUDY),
            new DaySession(at(5, 20, 0), at(5, 20, 30), 0, 2, PlanKind.PLAY));

    assertThat(DaySummary.of(OCT_5, plans, sessions))
        .isEqualTo(new DaySummary(45, 75, at(5, 10, 0), 45, 1));
  }

  @Test
  void playSessionDoesNotBecomeFirstStart() {
    // 아침에 논 기록이 먼저 있어도, 첫 시작 시각은 공부를 처음 시작한 시각입니다.
    List<DaySession> sessions =
        List.of(
            new DaySession(at(5, 8, 0), at(5, 9, 0), 0, 0, PlanKind.PLAY),
            new DaySession(at(5, 10, 0), at(5, 11, 0), 0, 0, PlanKind.STUDY));

    assertThat(DaySummary.of(OCT_5, List.of(), sessions).firstStartAt()).isEqualTo(at(5, 10, 0));
  }

  @Test
  void onlyPlayPlansMeansCannotCalculate() {
    // 노는 계획만 있는 날은 공부 목표가 0분이라 "계산 불가"(null) 입니다. 0% 가 아닙니다.
    List<DayPlan> plans = List.of(new DayPlan(OCT_5, 30, PlanKind.PLAY));

    assertThat(DaySummary.of(OCT_5, plans, List.of()).achievementRate()).isNull();
  }

  @Test
  void missingKindCountsAsStudy() {
    // 종류가 비어 있으면(null) 공부로 봅니다. (QuickCheckIn 과 같은 규칙)
    List<DayPlan> plans = List.of(new DayPlan(OCT_5, 60, null));
    List<DaySession> sessions = List.of(new DaySession(at(5, 10, 0), at(5, 10, 30), 0, 0, null));

    assertThat(DaySummary.of(OCT_5, plans, sessions).achievementRate()).isEqualTo(50);
  }
}
