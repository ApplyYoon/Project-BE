package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.GoalAdjust.Kind;
import com.ithopeschool.projectbe.domain.suggestion.ExperimentEffect;
import com.ithopeschool.projectbe.domain.suggestion.SuggestionGrade;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 목표 조정 제안 규칙(이슈 #63)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/goalAdjust.ts · goalAdjustInput.ts) 주석의 예시를 그대로 옮긴 것입니다.
class GoalAdjustTest {

  // ─── propose: 제안 만들기 ───

  @Test
  void feExampleRaiseTenPercent() {
    // FE 예) carrot-raise: 지금 목표 × 1.1 (반올림). 240 → 264
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 240, null))
        .isEqualTo(new GoalAdjust(Kind.RAISE, 240, 264));
  }

  @Test
  void feExampleLowerToRecentAverage() {
    // FE 예) goal-down: 최근 실제 평균 × 1.1 (반올림). 평균 100분, 지금 목표 240 → 110
    assertThat(GoalAdjust.propose(CareAction.GOAL_DOWN, 240, 100))
        .isEqualTo(new GoalAdjust(Kind.LOWER, 240, 110));
  }

  @Test
  void otherActionsDoNotPropose() {
    // 목표 조정이 아닌 대응(채찍·당근만·회복 우선 등)은 제안하지 않습니다. 최근 평균을 알든 모르든 같습니다.
    List<CareAction> others =
        List.of(
            CareAction.RECOVERY_FIRST,
            CareAction.CARROT_CHANGE_STRATEGY,
            CareAction.CARROT_KEEP,
            CareAction.KEEP_EASIER,
            CareAction.WHIP);

    for (CareAction action : others) {
      assertThat(GoalAdjust.propose(action, 240, 100)).as("%s", action).isNull();
      assertThat(GoalAdjust.propose(action, 240, null)).as("%s", action).isNull();
    }
  }

  @Test
  void noProposalWhenCurrentGoalIsZeroOrLess() {
    // 지금 목표가 0분 이하면 올리거나 낮출 기준이 없습니다.
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 0, null)).isNull();
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, -10, null)).isNull();
    assertThat(GoalAdjust.propose(CareAction.GOAL_DOWN, 0, 100)).isNull();
  }

  @Test
  void noLowerProposalWithoutRecentAverage() {
    // 최근 평균을 모르거나 0 이하면, 근거 없이 낮추지 않습니다.
    assertThat(GoalAdjust.propose(CareAction.GOAL_DOWN, 240, null)).isNull();
    assertThat(GoalAdjust.propose(CareAction.GOAL_DOWN, 240, 0)).isNull();
    assertThat(GoalAdjust.propose(CareAction.GOAL_DOWN, 240, -30)).isNull();
  }

  @Test
  void noLowerProposalWhenResultIsNotLower() {
    // "낮추자" 며 올리는 일을 막습니다. 계산한 값이 지금 목표보다 낮을 때만 제안합니다.
    // 평균 220분 × 1.1 = 242분 → 지금 목표 240분보다 높아서 제안 없음
    assertThat(GoalAdjust.propose(CareAction.GOAL_DOWN, 240, 220)).isNull();
    // 평균 218분 × 1.1 = 239.8 → 반올림하면 240분 → 지금과 같아서 제안 없음
    assertThat(GoalAdjust.propose(CareAction.GOAL_DOWN, 240, 218)).isNull();
    // 평균 217분 × 1.1 = 238.7 → 239분 → 1분이라도 낮으면 제안합니다
    assertThat(GoalAdjust.propose(CareAction.GOAL_DOWN, 240, 217))
        .isEqualTo(new GoalAdjust(Kind.LOWER, 240, 239));
  }

  @Test
  void noRaiseProposalWhenGoalIsTooSmall() {
    // 목표가 너무 작으면 10% 를 올려도 반올림했을 때 그대로라서 제안하지 않습니다.
    // 4분 × 1.1 = 4.4 → 4분 (그대로) → 제안 없음
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 4, null)).isNull();
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 1, null)).isNull();
    // 5분 × 1.1 = 5.5 → 6분 → 제안합니다
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 5, null))
        .isEqualTo(new GoalAdjust(Kind.RAISE, 5, 6));
  }

  @Test
  void raiseRoundsHalfUp() {
    // 반올림: 소수 첫째 자리가 5 이상이면 올립니다.
    // 15 × 1.1 = 16.5 → 17 / 25 × 1.1 = 27.5 → 28 / 100 × 1.1 = 110 / 90 × 1.1 = 99
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 15, null).toMinutes()).isEqualTo(17);
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 25, null).toMinutes()).isEqualTo(28);
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 100, null).toMinutes()).isEqualTo(110);
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 90, null).toMinutes()).isEqualTo(99);
  }

  @Test
  void raiseDoesNotLookAtRecentAverage() {
    // 올릴 때는 최근 평균을 보지 않습니다. 평균이 있어도 결과는 "지금 목표 × 1.1" 입니다.
    assertThat(GoalAdjust.propose(CareAction.CARROT_RAISE, 240, 50))
        .isEqualTo(new GoalAdjust(Kind.RAISE, 240, 264));
  }

  @Test
  void worksWithCareDecision() {
    // 이슈 #55 의 CareDecision.decide 결과를 그대로 넘겨서 이어지는지 확인합니다. (FE 이슈 #67 에서 확인한 시나리오)
    // 잘 시행 + 효과 있음 → 목표 240분을 264분으로 올리자고 제안
    CareDecision carrot =
        CareDecision.decide(SuggestionGrade.GOOD, ExperimentEffect.EFFECTIVE, null, 0, false);
    assertThat(GoalAdjust.propose(carrot.action(), 240, 100))
        .isEqualTo(new GoalAdjust(Kind.RAISE, 240, 264));

    // 못 시행 + 계획 과다, 최근 평균 100분 → 110분으로 낮추자고 제안
    CareDecision goalDown =
        CareDecision.decide(
            SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, MissReason.TOO_MUCH, 0, false);
    assertThat(GoalAdjust.propose(goalDown.action(), 240, 100))
        .isEqualTo(new GoalAdjust(Kind.LOWER, 240, 110));

    // 못 시행 + 폰(채찍) → 제안 없음
    CareDecision whip =
        CareDecision.decide(
            SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, MissReason.PHONE, 0, false);
    assertThat(GoalAdjust.propose(whip.action(), 240, 100)).isNull();

    // 3일 연속 미달(회복 우선) → 제안 없음
    CareDecision recovery =
        CareDecision.decide(
            SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, MissReason.TOO_MUCH, 3, false);
    assertThat(GoalAdjust.propose(recovery.action(), 240, 100)).isNull();

    // 부분 시행 → 제안 없음
    CareDecision partial =
        CareDecision.decide(SuggestionGrade.PARTIAL, ExperimentEffect.EFFECTIVE, null, 0, false);
    assertThat(GoalAdjust.propose(partial.action(), 240, 100)).isNull();

    // 잘 시행 + 효과 애매 → 제안 없음
    CareDecision unclear =
        CareDecision.decide(SuggestionGrade.GOOD, ExperimentEffect.UNCLEAR, null, 0, false);
    assertThat(GoalAdjust.propose(unclear.action(), 240, 100)).isNull();
  }

  // ─── recentAverageMinutes: 최근 실제 평균 ───

  @Test
  void feExampleRecentAverageSkipsRestDays() {
    // FE 예) 어제 60분, 그저께 0분(안 함), 3일 전 100분 → (60 + 100) ÷ 2 = 80
    assertThat(GoalAdjust.recentAverageMinutes(List.of(60, 0, 100))).isEqualTo(80);
  }

  @Test
  void recentAverageIsNullWithoutStudy() {
    // 공부한 날이 하루도 없으면 null 입니다. (0 이 아닙니다)
    assertThat(GoalAdjust.recentAverageMinutes(List.of())).isNull();
    assertThat(GoalAdjust.recentAverageMinutes(List.of(0, 0, 0, 0, 0, 0, 0))).isNull();
  }

  @Test
  void recentAverageLooksAtSevenDaysOnly() {
    // 어제부터 7일만 봅니다. 8일 전에 300분을 했어도 세지 않습니다.
    assertThat(GoalAdjust.recentAverageMinutes(List.of(0, 0, 0, 0, 0, 0, 0, 300))).isNull();
    assertThat(GoalAdjust.recentAverageMinutes(List.of(60, 60, 60, 60, 60, 60, 60, 300)))
        .isEqualTo(60);
  }

  @Test
  void recentAverageIsRounded() {
    // 평균은 반올림합니다. (50 + 51) ÷ 2 = 50.5 → 51 / (100 + 100 + 101) ÷ 3 = 100.33… → 100
    assertThat(GoalAdjust.recentAverageMinutes(List.of(50, 51))).isEqualTo(51);
    assertThat(GoalAdjust.recentAverageMinutes(List.of(100, 100, 101))).isEqualTo(100);
  }

  // ─── isDeclineSnoozed: 거절한 뒤 다시 묻지 않는 기간 ───

  @Test
  void feExampleSnoozedForThreeDays() {
    // FE 예) 3일로 정했고 10일에 거절 → 10·11·12일은 true, 13일부터 false
    LocalDate declined = LocalDate.of(2026, 10, 10);

    assertThat(GoalAdjust.isDeclineSnoozed(declined, LocalDate.of(2026, 10, 10))).isTrue();
    assertThat(GoalAdjust.isDeclineSnoozed(declined, LocalDate.of(2026, 10, 11))).isTrue();
    assertThat(GoalAdjust.isDeclineSnoozed(declined, LocalDate.of(2026, 10, 12))).isTrue();
    assertThat(GoalAdjust.isDeclineSnoozed(declined, LocalDate.of(2026, 10, 13))).isFalse();
    assertThat(GoalAdjust.isDeclineSnoozed(declined, LocalDate.of(2026, 10, 20))).isFalse();
  }

  @Test
  void feExampleNeverDeclinedIsNotSnoozed() {
    // FE 예) declinedDate 가 null(거절한 적 없음)이면 false
    assertThat(GoalAdjust.isDeclineSnoozed(null, LocalDate.of(2026, 10, 10))).isFalse();
  }

  @Test
  void futureDeclinedDateIsTreatedAsSnoozed() {
    // 거절한 날이 오늘보다 미래면 날짜가 꼬인 것입니다. 안전하게 "아직 쉬는 중" 으로 봅니다.
    assertThat(GoalAdjust.isDeclineSnoozed(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 10)))
        .isTrue();
  }

  @Test
  void snoozeCountsDaysAcrossMonths() {
    // 달이 바뀌어도 날짜를 맞게 셉니다. 9월 29일에 거절 → 9/29·9/30·10/1 은 true, 10/2 부터 false
    LocalDate declined = LocalDate.of(2026, 9, 29);

    assertThat(GoalAdjust.isDeclineSnoozed(declined, LocalDate.of(2026, 10, 1))).isTrue();
    assertThat(GoalAdjust.isDeclineSnoozed(declined, LocalDate.of(2026, 10, 2))).isFalse();
  }

  @Test
  void snoozeFollowsTheFourAmDayRule() {
    // "오늘" 은 새벽 4시에 바뀝니다. (DayRule) 10월 10일에 거절했을 때:
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    LocalDate declined = LocalDate.of(2026, 10, 10);
    Instant beforeFour = ZonedDateTime.of(2026, 10, 13, 3, 59, 0, 0, seoul).toInstant();
    Instant atFour = ZonedDateTime.of(2026, 10, 13, 4, 0, 0, 0, seoul).toInstant();

    // 10월 13일 03:59 는 아직 10월 12일 → 3일째 → 아직 쉬는 중
    assertThat(GoalAdjust.isDeclineSnoozed(declined, beforeFour)).isTrue();
    // 10월 13일 04:00 부터는 10월 13일 → 다시 물어도 됩니다
    assertThat(GoalAdjust.isDeclineSnoozed(declined, atFour)).isFalse();
  }

  // ─── scalePlanMinutes: 수락했을 때 계획마다 나누기 ───

  @Test
  void feExampleScaleKeepsTheSameRatio() {
    // FE 예) 30분·60분(합계 90) 을 135분으로 → 45분·90분
    assertThat(GoalAdjust.scalePlanMinutes(List.of(30, 60), 135)).containsExactly(45, 90);
  }

  @Test
  void scaleDownKeepsTheSameRatio() {
    // 낮출 때도 같은 비율입니다. 120분·120분(합계 240) 을 110분으로 → 55분·55분
    assertThat(GoalAdjust.scalePlanMinutes(List.of(120, 120), 110)).containsExactly(55, 55);
  }

  @Test
  void scaledTotalCanBeOffByRounding() {
    // 계획마다 반올림해서 합계가 조금 어긋날 수 있습니다. 10분 세 개(합계 30)를 10분으로 → 3분·3분·3분(합계 9)
    assertThat(GoalAdjust.scalePlanMinutes(List.of(10, 10, 10), 10)).containsExactly(3, 3, 3);
  }

  @Test
  void scaledPlanIsAtLeastOneMinute() {
    // 계획이 0분이 되지 않게 최소 1분으로 둡니다. 1분·100분(합계 101)을 10분으로 → 1분·10분
    assertThat(GoalAdjust.scalePlanMinutes(List.of(1, 100), 10)).containsExactly(1, 10);
  }

  @Test
  void scaleReturnsTheSameListWhenTotalIsZeroOrLess() {
    // 원래 합계나 새 합계가 0 이하면 비율을 구할 수 없어서 그대로 돌려줍니다.
    assertThat(GoalAdjust.scalePlanMinutes(List.of(0, 0), 100)).containsExactly(0, 0);
    assertThat(GoalAdjust.scalePlanMinutes(List.of(30, 60), 0)).containsExactly(30, 60);
    assertThat(GoalAdjust.scalePlanMinutes(List.of(30, 60), -5)).containsExactly(30, 60);
    assertThat(GoalAdjust.scalePlanMinutes(List.of(), 100)).isEmpty();
  }

  @Test
  void proposalAndScaleWorkTogether() {
    // 제안을 수락하는 흐름: 60분·180분(합계 240) → 264분 제안 → 계획마다 10% 씩 → 66분·198분
    GoalAdjust proposal = GoalAdjust.propose(CareAction.CARROT_RAISE, 240, null);

    assertThat(GoalAdjust.scalePlanMinutes(List.of(60, 180), proposal.toMinutes()))
        .containsExactly(66, 198);
  }
}
