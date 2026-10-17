package com.ithopeschool.projectbe.domain.psychtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 심리검사 채점(#219)이 FE src/lib/psychTest.ts 의 주석 예시와 같은 결과를 내는지 봅니다.
class PsychScoreTest {

  private static Map<PsychItem, Integer> answers(Object... pairs) {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (int i = 0; i < pairs.length; i += 2) {
      answers.put((PsychItem) pairs[i], (Integer) pairs[i + 1]);
    }
    return answers;
  }

  @Test
  @DisplayName("FE 주석의 예시: 척도 점수는 두 문장의 평균이고, 5 이상 high · 3 이하 low · 그 사이 mid")
  void feExamples() {
    Map<PsychScale, Double> scores =
        PsychScore.scoresOf(
            answers(
                PsychItem.S1,
                6,
                PsychItem.S2,
                5,
                PsychItem.A1,
                3,
                PsychItem.A2,
                2,
                PsychItem.P1,
                4,
                PsychItem.P2,
                5,
                PsychItem.C1,
                3,
                PsychItem.C2,
                3,
                PsychItem.F1,
                5,
                PsychItem.F2,
                5));

    assertThat(scores.get(PsychScale.START_RESISTANCE)).isEqualTo(5.5);
    assertThat(PsychScore.levelOf(5.5)).isEqualTo(PsychScore.Level.HIGH);
    assertThat(scores.get(PsychScale.DISTRACTION)).isEqualTo(2.5);
    assertThat(PsychScore.levelOf(2.5)).isEqualTo(PsychScore.Level.LOW);
    assertThat(scores.get(PsychScale.PLAN_OPTIMISM)).isEqualTo(4.5);
    assertThat(PsychScore.levelOf(4.5)).isEqualTo(PsychScore.Level.MID);
    // 3 "이하" 라서 3 도 낮음 · 5 "이상" 이라서 5 도 높음
    assertThat(scores.get(PsychScale.CARE_PREFERENCE)).isEqualTo(3.0);
    assertThat(PsychScore.levelOf(3.0)).isEqualTo(PsychScore.Level.LOW);
    assertThat(scores.get(PsychScale.FAIL_SENSITIVITY)).isEqualTo(5.0);
    assertThat(PsychScore.levelOf(5.0)).isEqualTo(PsychScore.Level.HIGH);
  }

  @Test
  @DisplayName("문장이 5개인 척도도 답한 문장들의 평균이다 (6 · 5 · 4 · 5 · 6 → 5.2)")
  void fiveItemsAverage() {
    Map<PsychScale, Double> scores =
        PsychScore.scoresOf(
            answers(
                PsychItem.S1,
                6,
                PsychItem.S2,
                5,
                PsychItem.S3,
                4,
                PsychItem.S4,
                5,
                PsychItem.S5,
                6));

    assertThat(scores.get(PsychScale.START_RESISTANCE)).isEqualTo(5.2);
  }

  @Test
  @DisplayName("답이 하나도 없는 척도는 가운데(4)이고, 1 ~ 7 밖의 답은 셈에서 뺀다")
  void missingAndInvalidAnswers() {
    Map<PsychScale, Double> scores =
        PsychScore.scoresOf(answers(PsychItem.R1, 6, PsychItem.R2, 9, PsychItem.E1, 0));

    assertThat(scores.get(PsychScale.RECORD_BURDEN)).isEqualTo(6.0);
    assertThat(scores.get(PsychScale.EXTERNAL_ATTRIBUTION)).isEqualTo(4.0);
    assertThat(scores.get(PsychScale.START_RESISTANCE)).isEqualTo(4.0);
    assertThat(scores).hasSize(7);
  }

  @Test
  @DisplayName("척도 이름은 FE 와 같은 camelCase, 높낮이는 high · mid · low 이고 문장은 척도마다 5개(옛 검사는 앞의 2개)다")
  void namesMatchFe() {
    assertThat(PsychScale.values())
        .extracting(PsychScale::toValue)
        .containsExactly(
            "startResistance",
            "distraction",
            "planOptimism",
            "recordBurden",
            "failSensitivity",
            "externalAttribution",
            "carePreference");
    assertThat(PsychScore.Level.values())
        .extracting(PsychScore.Level::toValue)
        .containsExactly("high", "mid", "low");
    for (PsychScale scale : PsychScale.values()) {
      long count =
          java.util.Arrays.stream(PsychItem.values()).filter(i -> i.scale() == scale).count();
      long old =
          java.util.Arrays.stream(PsychItem.values())
              .filter(i -> i.scale() == scale && i.isInOldTest())
              .count();
      assertThat(count).as(scale.toValue()).isEqualTo(5);
      assertThat(old).as(scale.toValue()).isEqualTo(2);
    }
  }
}
