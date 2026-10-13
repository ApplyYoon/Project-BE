package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.safety.LowMoodStreak.MoodRecord;
import com.ithopeschool.projectbe.domain.safety.SafetyBrake;
import com.ithopeschool.projectbe.domain.suggestion.ExperimentEffect;
import com.ithopeschool.projectbe.domain.suggestion.SuggestionGrade;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 어제 결과에서 "오늘의 대응 → 오늘의 관리 강도 · 목표 조정 제안" 까지 이어지는 흐름을 확인합니다.
//
//   이슈 #55  결과가 관리 강도(#62)와 목표 조정(#63)으로 넘어간다
//   이슈 #58  시험 기간이 끝나면 원래 한도로 돌아간다
//   이슈 #59  회복 모드가 되면 관리 강도가 0 이 된다
//
// 함수 하나하나의 규칙은 각자의 테스트가 확인합니다. (CareDecisionTest · CareLevelTest · GoalAdjustTest ·
// SafetyBrakeTest)
// 여기서는 앞 함수의 결과를 뒤 함수에 그대로 넣었을 때 맞게 이어지는지만 봅니다.
//
//   CareDecision.decide(...)  →  action
//   SafetyBrake.check(...)    →  stopWhip || recoveryMode
//       ↓
//   CareLevel.of(어제 단계, action, 한도, 브레이크)        →  오늘의 관리 강도
//   GoalAdjust.propose(action, 지금 목표, 최근 평균)       →  목표 조정 제안
class CareFlowTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 어제의 관리 강도 (2 = 넛지)
  private static final int YESTERDAY_LEVEL = 2;
  // 사용자가 정한 최대 한도 (4 = 강제적 채찍까지 허용)
  private static final int MAX_LEVEL = 4;
  // 지금 하루 목표(분)와 최근 실제 평균(분)
  private static final int CURRENT_MINUTES = 240;
  private static final int RECENT_AVERAGE_MINUTES = 100;

  // 하루의 결과를 한데 모은 것입니다.
  // action: 오늘의 대응 / level: 오늘의 관리 강도 / goalAdjust: 목표 조정 제안. 없으면 null
  private record Today(CareAction action, int level, GoalAdjust goalAdjust) {}

  // 안전 브레이크가 꺼진 평범한 날의 흐름입니다. 대응을 정하고, 그 결과를 단계와 목표 조정에 그대로 넘깁니다.
  private static Today flow(
      SuggestionGrade fulfillment,
      ExperimentEffect effect,
      MissReason mainMissReason,
      int consecutiveMissDays,
      boolean isMoodLow) {
    CareDecision decision =
        CareDecision.decide(fulfillment, effect, mainMissReason, consecutiveMissDays, isMoodLow);

    int level = CareLevel.of(YESTERDAY_LEVEL, decision.action(), MAX_LEVEL, false);
    GoalAdjust goalAdjust =
        GoalAdjust.propose(decision.action(), CURRENT_MINUTES, RECENT_AVERAGE_MINUTES);
    return new Today(decision.action(), level, goalAdjust);
  }

  // 한국 시간 10월 "day일 오전 9시" 를 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant morningOf(int day) {
    return ZonedDateTime.of(2026, 10, day, 9, 0, 0, 0, SEOUL).toInstant();
  }

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 관리 강도 계산에 넘기는 "안전 브레이크가 켜졌는지" 입니다. (SafetyBrake 주석의 사용법 그대로)
  private static boolean isOn(SafetyBrake brake) {
    return brake.stopWhip() || brake.recoveryMode();
  }

  // ─── 이슈 #55 의 표: 대응이 단계와 목표 조정으로 넘어간다 ───

  @Test
  void goodAndEffectiveLowersTheLevelAndRaisesTheGoal() {
    // 잘 시행 + 효과 있음 → 당근 + 목표 상향 → 단계는 두 칸 내려 0, 목표는 240분 → 264분(10% 상향) 제안
    Today today = flow(SuggestionGrade.GOOD, ExperimentEffect.EFFECTIVE, null, 0, false);

    assertThat(today.action()).isEqualTo(CareAction.CARROT_RAISE);
    assertThat(today.level()).isEqualTo(0);
    assertThat(today.goalAdjust()).isEqualTo(new GoalAdjust(GoalAdjust.Kind.RAISE, 240, 264));
  }

  @Test
  void goodButNoEffectLowersTheLevelAndKeepsTheGoal() {
    // 잘 시행 + 효과 없음 → 당근 + 전략 교체 → 단계는 두 칸 내려 0, 목표는 그대로(제안 없음)
    Today today = flow(SuggestionGrade.GOOD, ExperimentEffect.NO_EFFECT, null, 0, false);

    assertThat(today.action()).isEqualTo(CareAction.CARROT_CHANGE_STRATEGY);
    assertThat(today.level()).isEqualTo(0);
    assertThat(today.goalAdjust()).isNull();
  }

  @Test
  void partialKeepsBothTheLevelAndTheGoal() {
    // 부분 시행 → 난이도만 낮춤 → 단계 그대로 2, 목표도 그대로
    Today today = flow(SuggestionGrade.PARTIAL, ExperimentEffect.UNCLEAR, null, 0, false);

    assertThat(today.action()).isEqualTo(CareAction.KEEP_EASIER);
    assertThat(today.level()).isEqualTo(2);
    assertThat(today.goalAdjust()).isNull();
  }

  @Test
  void poorBecauseOfTooMuchLowersTheLevelAndTheGoal() {
    // 못 시행 + 계획 과다 → 목표 하향 → 단계는 한 칸 내려 1, 목표는 최근 평균 100분의 110% 인 110분으로 제안
    Today today =
        flow(SuggestionGrade.POOR, ExperimentEffect.UNCLEAR, MissReason.TOO_MUCH, 0, false);

    assertThat(today.action()).isEqualTo(CareAction.GOAL_DOWN);
    assertThat(today.level()).isEqualTo(1);
    assertThat(today.goalAdjust()).isEqualTo(new GoalAdjust(GoalAdjust.Kind.LOWER, 240, 110));
  }

  @Test
  void poorBecauseOfPhoneRaisesTheLevelByOneAndKeepsTheGoal() {
    // 못 시행 + 폰 → 채찍 → 단계는 한 칸만 올라 3, 목표는 그대로
    Today today = flow(SuggestionGrade.POOR, ExperimentEffect.UNCLEAR, MissReason.PHONE, 0, false);

    assertThat(today.action()).isEqualTo(CareAction.WHIP);
    assertThat(today.level()).isEqualTo(3);
    assertThat(today.goalAdjust()).isNull();
  }

  @Test
  void threeMissDaysGoToRecoveryEvenIfPhoneWasTheReason() {
    // 3일 연속 미달 → 원래라면 채찍(폰)이지만 회복 우선 → 단계 0, 목표 조정 제안 없음
    Today today = flow(SuggestionGrade.POOR, ExperimentEffect.UNCLEAR, MissReason.PHONE, 3, false);

    assertThat(today.action()).isEqualTo(CareAction.RECOVERY_FIRST);
    assertThat(today.level()).isEqualTo(0);
    assertThat(today.goalAdjust()).isNull();
  }

  @Test
  void lowMoodGoesToRecoveryEvenOnAGoodDay() {
    // 기분 저하 → 잘 지킨 날이어도 회복 우선 → 단계 0, 목표를 올리자는 제안도 하지 않습니다.
    Today today = flow(SuggestionGrade.GOOD, ExperimentEffect.EFFECTIVE, null, 0, true);

    assertThat(today.action()).isEqualTo(CareAction.RECOVERY_FIRST);
    assertThat(today.level()).isEqualTo(0);
    assertThat(today.goalAdjust()).isNull();
  }

  @Test
  void whipNeverGoesOverTheUsersLimit() {
    // 채찍이어도 사용자가 정한 한도(2)는 넘지 않습니다. 어제 2 → 오늘도 2
    CareDecision decision =
        CareDecision.decide(
            SuggestionGrade.POOR, ExperimentEffect.UNCLEAR, MissReason.PHONE, 0, false);

    assertThat(CareLevel.of(2, decision.action(), 2, false)).isEqualTo(2);
  }

  // ─── 이슈 #58: 시험 기간에는 0, 끝나면 원래 한도까지 다시 오른다 ───

  // 날마다 "채찍" 대응이 나오는 학생의 단계를 fromDay 부터 toDay 까지 하루씩 이어서 계산합니다.
  // 오늘의 단계가 다음 날의 "어제 단계" 가 됩니다.
  private static List<Integer> whipLevelsByDay(
      int startLevel, int maxLevel, int fromDay, int toDay, List<LocalDate> exams) {
    List<Integer> levels = new ArrayList<>();
    int level = startLevel;
    for (int day = fromDay; day <= toDay; day++) {
      SafetyBrake brake = SafetyBrake.check(morningOf(day), List.of(), List.of(), exams);
      level = CareLevel.of(level, CareAction.WHIP, maxLevel, isOn(brake));
      levels.add(level);
    }
    return levels;
  }

  @Test
  void examPeriodHoldsTheLevelAtZeroAndThenItClimbsBackToTheLimit() {
    // 시험은 10/17. 시험 기간은 10/14(3일 전) ~ 10/19(2일 후) 입니다. 어제 단계 3, 한도 4.
    //   10/13         시험 기간 전     →  3 에서 한 칸 올라 4
    //   10/14 ~ 10/19 시험 기간        →  채찍 대응이어도 0
    //   10/20 ~ 10/23 시험 기간이 끝남  →  하루에 한 칸씩 1, 2, 3, 4 (원래 한도 4 까지 다시 오릅니다)
    List<Integer> levels = whipLevelsByDay(3, 4, 13, 23, List.of(october(17)));

    assertThat(levels).containsExactly(4, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4);
  }

  @Test
  void afterTheExamPeriodTheUsersOwnLimitAppliesAgain() {
    // 한도가 2 인 사용자는 시험 기간이 끝난 뒤에도 2 에서 멈춥니다. (10/20 ~ 10/23)
    List<Integer> levels = whipLevelsByDay(0, 2, 20, 23, List.of(october(17)));

    assertThat(levels).containsExactly(1, 2, 2, 2);
  }

  // ─── 이슈 #59: 기분 저하가 이어지면 0, 기분이 돌아오면 바로 풀린다 ───

  // fromDay 부터 toDay 까지 날마다 같은 기분을 적은 기록을 만듭니다.
  private static List<MoodRecord> moods(int fromDay, int toDay, int mood) {
    List<MoodRecord> records = new ArrayList<>();
    for (int day = fromDay; day <= toDay; day++) {
      records.add(new MoodRecord(october(day), mood));
    }
    return records;
  }

  @Test
  void sixLowMoodDaysTurnTheLevelToZeroAndShowHelp() {
    // 10/05 ~ 10/10 여섯 날 모두 기분 2 → 회복 모드 + 도움 안내. 어제 3단계에 채찍 대응이어도 오늘은 0
    SafetyBrake brake = SafetyBrake.check(morningOf(10), List.of(), moods(5, 10, 2), List.of());

    assertThat(brake.recoveryMode()).isTrue();
    assertThat(brake.showHelp()).isTrue();
    assertThat(CareLevel.of(3, CareAction.WHIP, MAX_LEVEL, isOn(brake))).isEqualTo(0);
  }

  @Test
  void fiveLowMoodDaysAreNotEnough() {
    // 10/06 ~ 10/10 다섯 날만 기분 2 → 아직 회복 모드가 아니라서 평소대로 3 에서 4 로 오릅니다.
    SafetyBrake brake = SafetyBrake.check(morningOf(10), List.of(), moods(6, 10, 2), List.of());

    assertThat(brake.recoveryMode()).isFalse();
    assertThat(CareLevel.of(3, CareAction.WHIP, MAX_LEVEL, isOn(brake))).isEqualTo(4);
  }

  @Test
  void levelMovesAgainAsSoonAsTheMoodRecovers() {
    // 10/05 ~ 10/10 은 기분 2, 10/11 에 기분 4 를 적음 → 10/11 부터 회복 모드가 풀리고 0 에서 다시 한 칸 오릅니다.
    List<MoodRecord> records = moods(5, 10, 2);
    records.add(new MoodRecord(october(11), 4));
    SafetyBrake brake = SafetyBrake.check(morningOf(11), List.of(), records, List.of());

    assertThat(brake.recoveryMode()).isFalse();
    assertThat(CareLevel.of(0, CareAction.WHIP, MAX_LEVEL, isOn(brake))).isEqualTo(1);
  }

  // ─── 위기 표현: 어떤 대응·한도보다 먼저 0 ───

  @Test
  void crisisTextStopsTheWhipWhateverTheDecisionIs() {
    // 글에 위기 표현이 있으면 채찍을 즉시 멈춥니다. 어제 4단계에 채찍 대응이어도 오늘은 0 입니다.
    SafetyBrake brake = SafetyBrake.check(morningOf(10), List.of("죽고 싶다"), List.of(), List.of());

    assertThat(brake.stopWhip()).isTrue();
    assertThat(CareLevel.of(4, CareAction.WHIP, MAX_LEVEL, isOn(brake))).isEqualTo(0);
  }
}
