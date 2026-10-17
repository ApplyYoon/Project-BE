package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ithopeschool.projectbe.domain.suggestion.ExperimentEffect;
import com.ithopeschool.projectbe.domain.suggestion.SuggestionGrade;
import org.junit.jupiter.api.Test;

// "다음 대응 정하기" 규칙(이슈 #55)을 확인합니다.
// tableRow 로 시작하는 테스트는 이슈의 표 6줄이고,
// feExample 로 시작하는 테스트는 FE 함수(decideCareAction) 주석의 예시를 그대로 옮긴 것입니다.
class CareDecisionTest {

  // 회복 모드에 걸리지 않는 평범한 날의 값입니다. (연속 미달 0일, 기분 괜찮음)
  private static CareDecision decide(
      SuggestionGrade fulfillment, ExperimentEffect effect, MissReason mainMissReason) {
    return CareDecision.decide(fulfillment, effect, mainMissReason, 0, false);
  }

  // ---------- 이슈의 표 6줄 ----------

  @Test
  void tableRowGoodAndEffectiveIsCarrotRaise() {
    // 잘 시행 + 효과 있음 → 당근, 목표 10% 상향
    CareDecision decision = decide(SuggestionGrade.GOOD, ExperimentEffect.EFFECTIVE, null);

    assertThat(decision.action()).isEqualTo(CareAction.CARROT_RAISE);
    assertThat(decision.reason()).isEqualTo("잘 지켰고 효과도 있었어요. 목표를 10% 올려 볼게요.");
  }

  @Test
  void tableRowGoodAndNoEffectIsCarrotChangeStrategy() {
    // 잘 시행 + 효과 없음 → 당근(노력 인정), 전략 교체. 문제는 사람이 아니라 전략입니다.
    CareDecision decision = decide(SuggestionGrade.GOOD, ExperimentEffect.NO_EFFECT, null);

    assertThat(decision.action()).isEqualTo(CareAction.CARROT_CHANGE_STRATEGY);
    assertThat(decision.reason()).isEqualTo("잘 지켰는데 효과가 없었어요. 노력한 건 맞으니, 방법을 바꿔 볼게요.");
  }

  @Test
  void tableRowPartialIsKeepEasier() {
    // 부분 시행 → 같은 전략 유지, 난이도 한 단계 낮춤. 효과 판정이 무엇이든 같습니다.
    for (ExperimentEffect effect : ExperimentEffect.values()) {
      CareDecision decision = decide(SuggestionGrade.PARTIAL, effect, MissReason.PHONE);

      assertThat(decision.action()).isEqualTo(CareAction.KEEP_EASIER);
      assertThat(decision.reason()).isEqualTo("일부는 지켰어요. 같은 방법으로 두고, 난이도만 한 단계 낮출게요.");
    }
  }

  @Test
  void tableRowPoorWithBodyOrPlanReasonIsGoalDown() {
    // 못 시행 + 원인이 계획 과다·피곤·아픔·행사 → 목표 하향
    MissReason[] reasons = {
      MissReason.TOO_MUCH, MissReason.TIRED, MissReason.SICK, MissReason.EVENT
    };
    for (MissReason reason : reasons) {
      CareDecision decision = decide(SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, reason);

      assertThat(decision.action()).as("원인 %s", reason).isEqualTo(CareAction.GOAL_DOWN);
      assertThat(decision.reason()).isEqualTo("계획이 많았거나 몸이 힘들었던 것 같아요. 오늘은 목표를 낮출게요.");
    }
  }

  @Test
  void tableRowPoorWithHabitReasonIsWhip() {
    // 못 시행 + 원인이 폰·하기 싫음 → 채찍
    MissReason[] reasons = {MissReason.PHONE, MissReason.UNWILLING};
    for (MissReason reason : reasons) {
      CareDecision decision = decide(SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, reason);

      assertThat(decision.action()).as("원인 %s", reason).isEqualTo(CareAction.WHIP);
      assertThat(decision.reason()).isEqualTo("폰이나 미루는 습관 때문에 못 지켰어요. 오늘은 한 단계 더 따끔하게 말할게요.");
    }
  }

  @Test
  void tableRowThreeMissDaysIsRecoveryFirst() {
    // 3일 연속 미달 → 회복 모드 우선, 채찍 금지
    CareDecision decision =
        CareDecision.decide(SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, null, 3, false);

    assertThat(decision.action()).isEqualTo(CareAction.RECOVERY_FIRST);
    assertThat(decision.reason()).isEqualTo("3일 연속 계획을 못 채워서, 오늘은 다그치지 않고 회복부터 도울게요.");
  }

  @Test
  void tableRowLowMoodIsRecoveryFirst() {
    // 기분 저하 신호 → 회복 모드 우선, 채찍 금지
    CareDecision decision =
        CareDecision.decide(SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, null, 0, true);

    assertThat(decision.action()).isEqualTo(CareAction.RECOVERY_FIRST);
    assertThat(decision.reason()).isEqualTo("요즘 기분이 많이 가라앉아 보여서, 오늘은 다그치지 않고 회복부터 도울게요.");
  }

  // ---------- 회복 모드를 다른 모든 줄보다 먼저 확인하는지 ----------

  @Test
  void feExampleThreeMissDaysBlocksWhip() {
    // 3일 연속 미달, 폰 때문에 못 함 → 회복 우선 (원래라면 채찍이지만 막습니다)
    CareDecision decision =
        CareDecision.decide(
            SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, MissReason.PHONE, 3, false);

    assertThat(decision.action()).isEqualTo(CareAction.RECOVERY_FIRST);
  }

  @Test
  void feExampleLowMoodComesBeforeCarrot() {
    // 기분 저하, 오늘은 잘 했음 → 회복 우선 (잘 한 날도 회복 모드가 먼저)
    CareDecision decision =
        CareDecision.decide(SuggestionGrade.GOOD, ExperimentEffect.EFFECTIVE, null, 0, true);

    assertThat(decision.action()).isEqualTo(CareAction.RECOVERY_FIRST);
  }

  @Test
  void feExampleTwoMissDaysGoesToTable() {
    // 2일 연속 미달 → 아직 3일이 아니므로 표로 갑니다. (폰 때문에 못 했으면 채찍)
    CareDecision decision =
        CareDecision.decide(
            SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, MissReason.PHONE, 2, false);

    assertThat(decision.action()).isEqualTo(CareAction.WHIP);
  }

  @Test
  void recoveryFirstWinsForEveryCombination() {
    // 이행 판정·효과 판정·원인이 무엇이든, 3일 연속 미달이거나 기분 저하면 회복 우선입니다.
    for (SuggestionGrade fulfillment : SuggestionGrade.values()) {
      for (ExperimentEffect effect : ExperimentEffect.values()) {
        for (MissReason reason : MissReason.values()) {
          assertThat(CareDecision.decide(fulfillment, effect, reason, 3, false).action())
              .isEqualTo(CareAction.RECOVERY_FIRST);
          assertThat(CareDecision.decide(fulfillment, effect, reason, 0, true).action())
              .isEqualTo(CareAction.RECOVERY_FIRST);
        }
      }
    }
  }

  @Test
  void lowMoodReasonIsShownWhenBothSignalsAreOn() {
    // 연속 미달과 기분 저하가 같이 있으면 기분 이야기를 합니다. (FE 와 같은 문장)
    CareDecision decision =
        CareDecision.decide(SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, null, 5, true);

    assertThat(decision.reason()).isEqualTo("요즘 기분이 많이 가라앉아 보여서, 오늘은 다그치지 않고 회복부터 도울게요.");
  }

  @Test
  void missDaysAreWrittenInReason() {
    // 이유 문장에는 실제 연속 일수가 들어갑니다.
    CareDecision decision =
        CareDecision.decide(SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, null, 7, false);

    assertThat(decision.reason()).startsWith("7일 연속 계획을 못 채워서");
  }

  // ---------- 이슈의 표에 없어서 FE 와 같이 정해 둔 줄 ----------

  @Test
  void feExampleGoodAndUnclearIsCarrotKeep() {
    // 잘 시행 + 효과 애매 → 당근만. 근거 없이 목표를 올리지 않고 1주 더 지켜봅니다.
    CareDecision decision = decide(SuggestionGrade.GOOD, ExperimentEffect.UNCLEAR, null);

    assertThat(decision.action()).isEqualTo(CareAction.CARROT_KEEP);
    assertThat(decision.reason()).isEqualTo("잘 지켰어요. 효과는 아직 애매해서 1주 더 지켜볼게요.");
  }

  @Test
  void feExamplePoorWithEtcIsGoalDown() {
    // 못 시행 + 기타 → 목표 하향. 이유를 모를 때는 다그치지 않습니다.
    CareDecision decision =
        decide(SuggestionGrade.POOR, ExperimentEffect.NO_EFFECT, MissReason.ETC);

    assertThat(decision.action()).isEqualTo(CareAction.GOAL_DOWN);
    assertThat(decision.reason()).isEqualTo("못 지킨 이유를 아직 몰라서, 오늘은 다그치지 않고 목표를 낮출게요.");
  }

  @Test
  void poorWithoutReasonIsGoalDown() {
    // 못 시행 + 이유를 안 고름(null) → 기타와 똑같이 목표 하향
    CareDecision decision = decide(SuggestionGrade.POOR, ExperimentEffect.UNCLEAR, null);

    assertThat(decision.action()).isEqualTo(CareAction.GOAL_DOWN);
    assertThat(decision.reason()).isEqualTo("못 지킨 이유를 아직 몰라서, 오늘은 다그치지 않고 목표를 낮출게요.");
  }

  @Test
  void goodIgnoresMissReason() {
    // 잘 지킨 날은 못 채운 이유를 보지 않습니다. (이유가 폰이어도 채찍이 아닙니다)
    CareDecision decision =
        decide(SuggestionGrade.GOOD, ExperimentEffect.EFFECTIVE, MissReason.PHONE);

    assertThat(decision.action()).isEqualTo(CareAction.CARROT_RAISE);
  }

  // ---------- 판정이 없을 때 ----------

  @Test
  void missingGradeOrEffectIsRejected() {
    // 기록이 없어서 판정이 없는 경우는 부르는 쪽에서 먼저 처리해야 합니다. 그냥 넘기면 바로 알 수 있게 에러를 냅니다.
    assertThatThrownBy(() -> decide(null, ExperimentEffect.EFFECTIVE, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> decide(SuggestionGrade.GOOD, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
