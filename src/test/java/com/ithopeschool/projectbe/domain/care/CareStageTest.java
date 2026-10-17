package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.CareStage.Input;
import com.ithopeschool.projectbe.domain.care.CareStage.Recovery;
import com.ithopeschool.projectbe.domain.care.CareStage.Result;
import com.ithopeschool.projectbe.domain.care.CareStage.Rule;
import com.ithopeschool.projectbe.domain.care.CareStage.Safety;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 관리 단계 매일 판정을 FE src/lib/careStage.ts 주석의 예시 그대로 확인합니다. (이슈 #62)
// 예시의 기본값: 어제 2단계, 기준 단계 2, 한도 4, 20일째, 시험 아님, 민감도 3
class CareStageTest {

  private static final Safety SAFE = new Safety(false, false);
  private static final Recovery NO_RECOVERY = new Recovery(0, 0, false, false, 3);
  private static final RelapseSignal NO_SIGNAL =
      new RelapseSignal(false, List.of(), List.of(), null, null, null);

  // 같은 루틴을 2번 연속 놓친 재발 신호 (주원인 cause)
  private static RelapseSignal signal(MissReason cause) {
    return new RelapseSignal(
        true, List.of(RelapseReason.ROUTINE_TWICE), List.of(1L), null, null, cause);
  }

  // 예시의 기본값에서 재발 신호만 바꾼 입력
  private static Input input(RelapseSignal relapse) {
    return new Input(2, 2, 20, SAFE, NO_RECOVERY, relapse, 4, false);
  }

  @Test
  @DisplayName("위기 표현 → 0, 안전")
  void crisis() {
    Input input = new Input(2, 2, 20, new Safety(true, false), NO_RECOVERY, NO_SIGNAL, 4, false);

    Result result = CareStage.decide(input);

    assertThat(result.stage()).isZero();
    assertThat(result.rule()).isEqualTo(Rule.SAFETY);
    assertThat(result.suggestShrink()).isFalse();
    assertThat(result.reason()).isEqualTo("요즘 많이 힘들어 보여서 오늘은 쉬어 가는 회복 모드야.");
  }

  @Test
  @DisplayName("안전 조건은 재발 신호보다 먼저 봅니다: 기분 저하 + 폰 재발 신호 → 0, 안전")
  void lowMoodBeforeRelapse() {
    Input input =
        new Input(
            2, 2, 20, new Safety(false, true), NO_RECOVERY, signal(MissReason.PHONE), 4, false);

    assertThat(CareStage.decide(input).rule()).isEqualTo(Rule.SAFETY);
    assertThat(CareStage.decide(input).stage()).isZero();
  }

  @Test
  @DisplayName("휴식·미실행 3일 연속 (민감도 3) → 0, 회복 + 계획 축소 제안")
  void recoveryByMissStreak() {
    Input input =
        new Input(2, 2, 20, SAFE, new Recovery(3, 0, false, false, 3), NO_SIGNAL, 4, false);

    Result result = CareStage.decide(input);

    assertThat(result.stage()).isZero();
    assertThat(result.rule()).isEqualTo(Rule.RECOVERY);
    assertThat(result.suggestShrink()).isTrue();
    assertThat(result.reason()).isEqualTo("요 며칠 버거웠던 것 같아서 회복 모드야. 오늘은 계획을 줄여서 작게 시작해 보자.");
  }

  @Test
  @DisplayName("회복 조건: 2일은 민감도 3 에 못 미치고, 부정 감정 연속 · 절대 카드 · 힘들다는 말은 회복")
  void recoveryConditions() {
    Input twoDays =
        new Input(2, 2, 20, SAFE, new Recovery(2, 2, false, false, 3), NO_SIGNAL, 4, false);
    Input negativeMood =
        new Input(2, 2, 20, SAFE, new Recovery(0, 3, false, false, 3), NO_SIGNAL, 4, false);
    Input absoluteMissed =
        new Input(2, 2, 20, SAFE, new Recovery(0, 0, true, false, 3), NO_SIGNAL, 4, false);
    Input hardTalk =
        new Input(2, 2, 20, SAFE, new Recovery(0, 0, false, true, 3), NO_SIGNAL, 4, false);
    Input sensitiveTwoDays =
        new Input(2, 2, 20, SAFE, new Recovery(2, 0, false, false, 2), NO_SIGNAL, 4, false);

    assertThat(CareStage.decide(twoDays).rule()).isEqualTo(Rule.TO_BASE);
    assertThat(CareStage.decide(negativeMood).rule()).isEqualTo(Rule.RECOVERY);
    assertThat(CareStage.decide(absoluteMissed).rule()).isEqualTo(Rule.RECOVERY);
    assertThat(CareStage.decide(hardTalk).rule()).isEqualTo(Rule.RECOVERY);
    assertThat(CareStage.decide(sensitiveTwoDays).rule()).isEqualTo(Rule.RECOVERY);
  }

  @Test
  @DisplayName("재발 신호 + 주원인 폰 → 3, 올림")
  void relapseByPhoneGoesUp() {
    Result result = CareStage.decide(input(signal(MissReason.PHONE)));

    assertThat(result.stage()).isEqualTo(3);
    assertThat(result.rule()).isEqualTo(Rule.UP);
    assertThat(result.suggestShrink()).isFalse();
    assertThat(result.reason()).isEqualTo("같은 루틴을 2번 연속 놓쳤고 주로 폰·SNS 때문이라 심리적 채찍 모드야.");
  }

  @Test
  @DisplayName("재발 신호 + 주원인 폰, 한도 2 → 2 (한도는 넘지 않고, 문장도 넛지)")
  void relapseCappedByMaxLevel() {
    Input input = new Input(2, 2, 20, SAFE, NO_RECOVERY, signal(MissReason.PHONE), 2, false);

    Result result = CareStage.decide(input);

    assertThat(result.stage()).isEqualTo(2);
    assertThat(result.rule()).isEqualTo(Rule.UP);
    assertThat(result.reason()).isEqualTo("같은 루틴을 2번 연속 놓쳤고 주로 폰·SNS 때문이라 넛지 모드야.");
  }

  @Test
  @DisplayName("재발 신호 + 주원인 폰, 시험 기간 → 2 (시험 기간 상한 2)")
  void relapseCappedByExamPeriod() {
    Input input = new Input(2, 2, 20, SAFE, NO_RECOVERY, signal(MissReason.PHONE), 4, true);

    Result result = CareStage.decide(input);

    assertThat(result.stage()).isEqualTo(CareStage.EXAM_STAGE_CAP);
    assertThat(result.rule()).isEqualTo(Rule.UP);
  }

  @Test
  @DisplayName("처음 7일은 2 까지: 6일째 폰 재발 신호 → 2, 7일째 → 3")
  void firstDaysCap() {
    Input sixth = new Input(2, 2, 6, SAFE, NO_RECOVERY, signal(MissReason.PHONE), 4, false);
    Input seventh = new Input(2, 2, 7, SAFE, NO_RECOVERY, signal(MissReason.PHONE), 4, false);

    assertThat(CareStage.decide(sixth).stage()).isEqualTo(CareStage.FIRST_DAYS_STAGE_CAP);
    assertThat(CareStage.decide(seventh).stage()).isEqualTo(3);
  }

  @Test
  @DisplayName("재발 신호 + 하기 싫음 · 미루다 보니도 내 쪽이라 올림")
  void otherInternalCauses() {
    Result unwilling = CareStage.decide(input(signal(MissReason.UNWILLING)));
    Result procrastinated = CareStage.decide(input(signal(MissReason.PROCRASTINATED)));

    assertThat(unwilling.stage()).isEqualTo(3);
    assertThat(unwilling.reason()).isEqualTo("같은 루틴을 2번 연속 놓쳤고 주로 하기 싫은 마음 때문이라 심리적 채찍 모드야.");
    assertThat(procrastinated.stage()).isEqualTo(3);
    assertThat(procrastinated.reason()).isEqualTo("같은 루틴을 2번 연속 놓쳤고 주로 미루다 보니 때문이라 심리적 채찍 모드야.");
  }

  @Test
  @DisplayName("재발 신호 + 주원인 아픔 → 2, 그대로 + 계획 축소 제안")
  void relapseBySickHolds() {
    Result result = CareStage.decide(input(signal(MissReason.SICK)));

    assertThat(result.stage()).isEqualTo(2);
    assertThat(result.rule()).isEqualTo(Rule.HOLD_UNAVOIDABLE);
    assertThat(result.suggestShrink()).isTrue();
    assertThat(result.reason())
        .isEqualTo("같은 루틴을 2번 연속 놓쳤고 아픔·약속처럼 어쩔 수 없는 일이 많아서 단계는 그대로야. 계획을 줄여 볼까?");
  }

  @Test
  @DisplayName("재발 신호 + 주원인 피곤 → 2, 그대로 + 계획 축소 제안")
  void relapseByTiredHolds() {
    Result result = CareStage.decide(input(signal(MissReason.TIRED)));

    assertThat(result.stage()).isEqualTo(2);
    assertThat(result.rule()).isEqualTo(Rule.HOLD_OVERLOAD);
    assertThat(result.suggestShrink()).isTrue();
    assertThat(result.reason())
        .isEqualTo("같은 루틴을 2번 연속 놓쳤고 계획이 많았거나 피곤했던 날이 많아서 단계는 그대로야. 계획을 줄여 볼까?");
  }

  @Test
  @DisplayName("재발 신호 + 주원인을 모름(기타 · 없음) → 그대로, 계획 축소 제안은 안 함")
  void relapseByUnknownHolds() {
    Result etc = CareStage.decide(input(signal(MissReason.ETC)));
    Result none = CareStage.decide(input(signal(null)));

    assertThat(etc.stage()).isEqualTo(2);
    assertThat(etc.rule()).isEqualTo(Rule.HOLD_UNKNOWN);
    assertThat(etc.suggestShrink()).isFalse();
    assertThat(etc.reason())
        .isEqualTo("같은 루틴을 2번 연속 놓쳤고 아직 이유를 몰라서 단계는 그대로야. 무슨 일이 있었는지 통화에서 물어볼게.");
    assertThat(none.rule()).isEqualTo(Rule.HOLD_UNKNOWN);
  }

  @Test
  @DisplayName("재발 신호의 이유가 여러 개면 \", \" 로 잇습니다")
  void severalReasons() {
    RelapseSignal relapse =
        new RelapseSignal(
            true,
            List.of(RelapseReason.RATE_DROP, RelapseReason.ABSOLUTE_MISS),
            List.of(),
            0.3,
            0.8,
            MissReason.PHONE);

    assertThat(CareStage.decide(input(relapse)).reason())
        .isEqualTo("최근 7일 실행률이 평소보다 많이 떨어졌고, 꼭 해야 하는 카드를 놓쳤고 주로 폰·SNS 때문이라 심리적 채찍 모드야.");
  }

  @Test
  @DisplayName("재발 신호 없음, 기준 단계 1 → 1, 기준 쪽으로")
  void noSignalStepsDownToBase() {
    Input input = new Input(2, 1, 20, SAFE, NO_RECOVERY, NO_SIGNAL, 4, false);

    Result result = CareStage.decide(input);

    assertThat(result.stage()).isEqualTo(1);
    assertThat(result.rule()).isEqualTo(Rule.TO_BASE);
    assertThat(result.suggestShrink()).isFalse();
    assertThat(result.reason()).isEqualTo(CareLevelInfo.of(1).defaultReason());
  }

  @Test
  @DisplayName("재발 신호 없음, 어제 0(회복), 기준 단계 2 → 1 (한 칸씩)")
  void noSignalStepsUpOneAtATime() {
    Input input = new Input(0, 2, 20, SAFE, NO_RECOVERY, NO_SIGNAL, 4, false);

    Result result = CareStage.decide(input);

    assertThat(result.stage()).isEqualTo(1);
    assertThat(result.rule()).isEqualTo(Rule.TO_BASE);
  }

  @Test
  @DisplayName("처음 판정(어제 단계 없음)은 기준 단계에서 시작합니다")
  void firstDecisionStartsAtBase() {
    Input input = new Input(null, 2, 0, SAFE, NO_RECOVERY, NO_SIGNAL, 4, false);

    Result result = CareStage.decide(input);

    assertThat(result.stage()).isEqualTo(2);
    assertThat(result.reason()).isEqualTo(CareLevelInfo.of(2).defaultReason());
  }

  @Test
  @DisplayName("어제 4단계, 재발 신호 + 폰 → 4 (최대 4)")
  void neverAboveFour() {
    Input input = new Input(4, 2, 20, SAFE, NO_RECOVERY, signal(MissReason.PHONE), 4, false);

    Result result = CareStage.decide(input);

    assertThat(result.stage()).isEqualTo(4);
    assertThat(result.reason()).isEqualTo("같은 루틴을 2번 연속 놓쳤고 주로 폰·SNS 때문이라 강제적 채찍 모드야.");
  }

  @Test
  @DisplayName("한도를 낮춘 날은 기준 쪽 판정이어도 바로 한도로 내려갑니다: 어제 4, 기준 4, 한도 1 → 1")
  void maxLevelLowersImmediately() {
    Input input = new Input(4, 4, 20, SAFE, NO_RECOVERY, NO_SIGNAL, 1, false);

    Result result = CareStage.decide(input);

    assertThat(result.stage()).isEqualTo(1);
    assertThat(result.reason()).isEqualTo(CareLevelInfo.of(1).defaultReason());
  }

  @Test
  @DisplayName("성공하면 한 칸 내림: 3 → 2, 1 → 1, 0 → 0")
  void stepDownOnSuccess() {
    assertThat(CareStage.stepDownOnSuccess(4)).isEqualTo(3);
    assertThat(CareStage.stepDownOnSuccess(3)).isEqualTo(2);
    assertThat(CareStage.stepDownOnSuccess(2)).isEqualTo(1);
    assertThat(CareStage.stepDownOnSuccess(1)).isEqualTo(1);
    assertThat(CareStage.stepDownOnSuccess(0)).isZero();
  }

  @Test
  @DisplayName("FE 와 주고받는 규칙 글자")
  void ruleValues() {
    assertThat(Rule.HOLD_UNAVOIDABLE.toValue()).isEqualTo("holdUnavoidable");
    assertThat(Rule.TO_BASE.toValue()).isEqualTo("toBase");
  }
}
