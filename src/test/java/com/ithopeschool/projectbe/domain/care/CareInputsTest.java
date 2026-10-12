package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.CareInputs.ActiveSuggestion;
import com.ithopeschool.projectbe.domain.care.CareInputs.DayCheckIn;
import com.ithopeschool.projectbe.domain.care.CareInputs.SuggestionCheck;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DayPlan;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 오늘의 대응에 넣을 재료를 모으는 규칙(이슈 #55)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/careInputs.ts · src/lib/goalAdjustInput.ts) 주석의 예시를 옮긴 것입니다.
// 개선안은 10월 15일에 시작한 14일짜리입니다. (기준선 10/1 ~ 10/14, 실험 10/15 ~ 10/28, 10/29 부터 끝난 개선안)
// 오늘은 10월 30일입니다.
class CareInputsTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
  private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 29);

  private static final ActiveSuggestion SUGGESTION =
      new ActiveSuggestion(1L, LocalDate.of(2026, 10, 15), 14);

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 10월 from 일부터 to 일까지 매일 "계획 targetMinutes 분" 을 만듭니다.
  private static List<DayPlan> plansEveryDay(int from, int to, int targetMinutes) {
    List<DayPlan> plans = new ArrayList<>();
    for (int day = from; day <= to; day++) {
      plans.add(new DayPlan(october(day), targetMinutes));
    }
    return plans;
  }

  // 10월 from 일부터 to 일까지 매일 저녁 7시에 minutes 분 공부한 기록을 만듭니다.
  private static List<DaySession> studyEveryDay(int from, int to, int minutes) {
    List<DaySession> sessions = new ArrayList<>();
    for (int day = from; day <= to; day++) {
      Instant startedAt = october(day).atTime(19, 0).atZone(SEOUL).toInstant();
      sessions.add(new DaySession(startedAt, startedAt.plusSeconds(minutes * 60L), 0, 0));
    }
    return sessions;
  }

  // 1번 개선안을 10월 from 일부터 to 일까지 매일 같은 답(kept)을 한 이행 기록을 만듭니다.
  private static List<SuggestionCheck> checksEveryDay(int from, int to, boolean kept) {
    List<SuggestionCheck> checks = new ArrayList<>();
    for (int day = from; day <= to; day++) {
      checks.add(new SuggestionCheck(1L, october(day), kept));
    }
    return checks;
  }

  // 계획(매일 120분)과 공부 기록: 기준선에는 매일 baselineMinutes 분, 실험 기간과 어제(10/29)에는 매일 experimentMinutes 분
  private static List<DaySession> study(int baselineMinutes, int experimentMinutes) {
    List<DaySession> sessions = new ArrayList<>();
    sessions.addAll(studyEveryDay(1, 14, baselineMinutes));
    sessions.addAll(studyEveryDay(15, 29, experimentMinutes));
    return sessions;
  }

  private static final List<DayPlan> PLANS = plansEveryDay(1, 29, 120);

  private static CareDecision decision(
      List<SuggestionCheck> checks, List<DayCheckIn> checkIns, List<DaySession> sessions) {
    return CareInputs.todayDecision(List.of(SUGGESTION), checks, checkIns, PLANS, sessions, TODAY);
  }

  // ─── 오늘의 대응 ───

  @Test
  void wellKeptAndEffectiveGivesCarrotAndRaise() {
    // 14일을 모두 지켰고, 순공이 60분에서 120분으로 늘었습니다. 어제도 계획(120분)을 다 채웠습니다.
    CareDecision decision = decision(checksEveryDay(15, 28, true), List.of(), study(60, 120));

    assertThat(decision.action()).isEqualTo(CareAction.CARROT_RAISE);
  }

  @Test
  void wellKeptButNoEffectChangesTheStrategy() {
    // 14일을 모두 지켰는데 순공이 그대로(120분)입니다. 문제는 사람이 아니라 전략입니다.
    CareDecision decision = decision(checksEveryDay(15, 28, true), List.of(), study(120, 120));

    assertThat(decision.action()).isEqualTo(CareAction.CARROT_CHANGE_STRATEGY);
  }

  @Test
  void notKeptBecauseOfPhoneGivesWhip() {
    // 14일 중 3일만 지켰고(21%), 오늘 체크인에서 못 채운 이유로 "폰" 을 골랐습니다. 어제는 계획을 다 채웠습니다.
    List<SuggestionCheck> checks = new ArrayList<>();
    checks.addAll(checksEveryDay(15, 17, true));
    checks.addAll(checksEveryDay(18, 28, false));
    List<DayCheckIn> checkIns = List.of(new DayCheckIn(TODAY, 3, MissReason.PHONE));

    assertThat(decision(checks, checkIns, study(120, 120)).action()).isEqualTo(CareAction.WHIP);
  }

  @Test
  void lowMoodComesBeforeEverythingElse() {
    // 잘 지켰고 효과도 있었지만 오늘 기분이 2 입니다. 회복이 먼저입니다.
    List<DayCheckIn> checkIns = List.of(new DayCheckIn(TODAY, 2, null));

    CareDecision decision = decision(checksEveryDay(15, 28, true), checkIns, study(60, 120));

    assertThat(decision.action()).isEqualTo(CareAction.RECOVERY_FIRST);
    assertThat(decision.reason()).contains("기분");
  }

  @Test
  void threeMissDaysInARowComesBeforeTheWhip() {
    // 못 지켰고 이유도 "폰" 이라 원래는 채찍이지만, 최근 3일(10/27 ~ 10/29)을 연속으로 못 채웠습니다.
    List<DayCheckIn> checkIns = List.of(new DayCheckIn(TODAY, 3, MissReason.PHONE));
    List<DaySession> sessions = new ArrayList<>();
    sessions.addAll(studyEveryDay(1, 26, 120));
    sessions.addAll(studyEveryDay(27, 29, 30));

    CareDecision decision = decision(checksEveryDay(15, 28, false), checkIns, sessions);

    assertThat(decision.action()).isEqualTo(CareAction.RECOVERY_FIRST);
    assertThat(decision.reason()).startsWith("3일 연속");
  }

  @Test
  void noDecisionWithoutAFinishedSuggestion() {
    // 실험 마지막 날(10/28)에는 아직 진행 중이라 대응을 정하지 않습니다.
    assertThat(
            CareInputs.todayDecision(
                List.of(SUGGESTION),
                checksEveryDay(15, 27, true),
                List.of(),
                PLANS,
                study(60, 120),
                october(28)))
        .isNull();
    // 도전 중인 개선안이 아예 없을 때도 같습니다.
    assertThat(
            CareInputs.todayDecision(List.of(), List.of(), List.of(), PLANS, study(60, 120), TODAY))
        .isNull();
  }

  @Test
  void noDecisionWithoutAnyCheckOfThatSuggestion() {
    // 끝난 개선안은 있지만 이행 기록이 하나도 없습니다. "0% 못 시행" 이 아니라 "모름" 이라 정하지 않습니다.
    assertThat(decision(List.of(), List.of(), study(60, 120))).isNull();
    // 다른 개선안(2번)의 기록만 있을 때도 같습니다.
    List<SuggestionCheck> othersChecks = List.of(new SuggestionCheck(2L, october(20), true));
    assertThat(decision(othersChecks, List.of(), study(60, 120))).isNull();
  }

  // ─── 끝난 개선안 고르기 ───

  @Test
  void feExampleSuggestionIsFinishedTheDayAfterItsLastDay() {
    // FE 예) 10/1 시작, 14일 → 10/14 까지가 실험 기간이라서, 10/15 부터 "끝난" 개선안입니다.
    ActiveSuggestion suggestion = new ActiveSuggestion(1L, october(1), 14);

    assertThat(CareInputs.pickFinished(List.of(suggestion), october(14))).isNull();
    assertThat(CareInputs.pickFinished(List.of(suggestion), october(15))).isEqualTo(suggestion);
  }

  @Test
  void mostRecentlyFinishedSuggestionIsPicked() {
    ActiveSuggestion endedOn15 = new ActiveSuggestion(1L, october(1), 14);
    ActiveSuggestion endedOn22 = new ActiveSuggestion(2L, october(15), 7);
    ActiveSuggestion stillGoing = new ActiveSuggestion(3L, october(25), 14);

    assertThat(CareInputs.pickFinished(List.of(endedOn15, stillGoing, endedOn22), TODAY))
        .isEqualTo(endedOn22);
  }

  @Test
  void firstInTheListWinsWhenTheyEndedOnTheSameDay() {
    // 10/8 시작 14일과 10/15 시작 7일은 둘 다 10/22 에 끝납니다.
    ActiveSuggestion first = new ActiveSuggestion(7L, october(8), 14);
    ActiveSuggestion second = new ActiveSuggestion(3L, october(15), 7);

    assertThat(CareInputs.pickFinished(List.of(first, second), TODAY)).isEqualTo(first);
  }

  // ─── 체크인에서 꺼내기 ───

  @Test
  void todaysCheckInComesBeforeYesterdays() {
    DayCheckIn today = new DayCheckIn(TODAY, 4, null);
    DayCheckIn yesterday = new DayCheckIn(YESTERDAY, 1, MissReason.TIRED);

    // 오늘 체크인이 있으면 그것만 봅니다. 어제의 낮은 기분과 이유는 쓰지 않습니다.
    assertThat(CareInputs.latestCheckIn(List.of(yesterday, today), TODAY)).isEqualTo(today);
    assertThat(CareInputs.isMoodLow(List.of(yesterday, today), TODAY)).isFalse();
    assertThat(CareInputs.mainMissReason(List.of(yesterday, today), TODAY)).isNull();
  }

  @Test
  void yesterdaysCheckInIsUsedWhenTodayHasNone() {
    DayCheckIn yesterday = new DayCheckIn(YESTERDAY, 1, MissReason.TIRED);

    assertThat(CareInputs.isMoodLow(List.of(yesterday), TODAY)).isTrue();
    assertThat(CareInputs.mainMissReason(List.of(yesterday), TODAY)).isEqualTo(MissReason.TIRED);
  }

  @Test
  void olderCheckInsAreIgnored() {
    // 그저께 체크인은 보지 않습니다. 체크인이 없으면 기분이 낮다고 보지 않습니다.
    DayCheckIn twoDaysAgo = new DayCheckIn(october(28), 1, MissReason.PHONE);

    assertThat(CareInputs.latestCheckIn(List.of(twoDaysAgo), TODAY)).isNull();
    assertThat(CareInputs.isMoodLow(List.of(twoDaysAgo), TODAY)).isFalse();
    assertThat(CareInputs.mainMissReason(List.of(twoDaysAgo), TODAY)).isNull();
  }

  @Test
  void moodTwoIsLowAndThreeIsNot() {
    assertThat(CareInputs.isMoodLow(List.of(new DayCheckIn(TODAY, 2, null)), TODAY)).isTrue();
    assertThat(CareInputs.isMoodLow(List.of(new DayCheckIn(TODAY, 3, null)), TODAY)).isFalse();
  }

  // ─── 연속 미달 일수 ───

  @Test
  void feExampleMissDaysStopAtAFullDay() {
    // FE 예) 어제 50%, 그저께 80%, 3일 전 100% → 2
    List<DayPlan> plans = plansEveryDay(27, 29, 100);
    List<DaySession> sessions = new ArrayList<>();
    sessions.addAll(studyEveryDay(29, 29, 50));
    sessions.addAll(studyEveryDay(28, 28, 80));
    sessions.addAll(studyEveryDay(27, 27, 100));

    assertThat(CareInputs.consecutiveMissDays(plans, sessions, TODAY)).isEqualTo(2);
  }

  @Test
  void feExampleNoPlanYesterdayMeansZero() {
    // FE 예) 어제 계획 없음 → 0 (그저께 못 채웠어도 어제에서 멈춥니다)
    List<DayPlan> plans = plansEveryDay(27, 28, 100);

    assertThat(CareInputs.consecutiveMissDays(plans, List.of(), TODAY)).isZero();
  }

  @Test
  void todayIsNotCounted() {
    // 오늘(10/30)은 아직 끝나지 않아서 세지 않습니다. 어제 하루만 못 채웠습니다.
    List<DayPlan> plans = plansEveryDay(29, 30, 100);

    assertThat(CareInputs.consecutiveMissDays(plans, List.of(), TODAY)).isEqualTo(1);
  }

  @Test
  void missDaysLookBackThirtyDaysAtMost() {
    // 9월 1일부터 매일 계획만 있고 공부를 안 했습니다. 59일을 못 채웠지만 30일까지만 셉니다.
    List<DayPlan> plans = new ArrayList<>();
    for (LocalDate date = LocalDate.of(2026, 9, 1); date.isBefore(TODAY); date = date.plusDays(1)) {
      plans.add(new DayPlan(date, 60));
    }

    assertThat(CareInputs.consecutiveMissDays(plans, List.of(), TODAY)).isEqualTo(30);
  }
}
