package com.ithopeschool.projectbe.domain.thinkingtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 4축 공부 스타일과 "많이 해당하는 습관" 규칙을 확인합니다. FE 의 scoreThinkingTest 와 같은 결과여야 합니다.
class StudyStyleTest {

  // 모든 습관을 value 로 채운 점수
  private static Map<CognitiveBias, Double> all(double value) {
    Map<CognitiveBias, Double> scores = new EnumMap<>(CognitiveBias.class);
    for (CognitiveBias bias : CognitiveBias.values()) {
      scores.put(bias, value);
    }
    return scores;
  }

  @Test
  @DisplayName("모두 4(가운데)면 네 축 모두 '균형' 이고 많이 해당하는 습관이 없다")
  void middleIsBalanced() {
    assertThat(StudyStyle.styleNameOf(all(4.0))).isEqualTo("균형 · 균형 · 균형 · 균형");
    assertThat(StudyStyle.topHabitsOf(all(4.0))).isEmpty();
  }

  @Test
  @DisplayName("축 평균이 4.5 이상이면 높은 쪽, 3.5 이하면 낮은 쪽 이름이다 (경계 포함)")
  void thresholds() {
    Map<CognitiveBias, Double> scores = all(4.0);
    // 계획할 때: (5.0 + 4.0) / 2 = 4.5 → 낙관
    scores.put(CognitiveBias.PLANNING_FALLACY, 5.0);
    // 넘어졌을 때: (3.0 + 4.0) / 2 = 3.5 → 회복
    scores.put(CognitiveBias.WHAT_THE_HELL, 3.0);
    // 움직이는 힘: (4.4 + 4.4) / 2 = 4.4 → 균형
    scores.put(CognitiveBias.EMOTIONAL_REASONING, 4.4);
    scores.put(CognitiveBias.MORAL_LICENSING, 4.4);
    // 원인을 볼 때: (1.0 + 2.0) / 2 = 1.5 → 성장
    scores.put(CognitiveBias.EXTERNAL_BLAME, 1.0);
    scores.put(CognitiveBias.OVERGENERALIZING, 2.0);

    assertThat(StudyStyle.styleNameOf(scores)).isEqualTo("낙관 · 회복 · 균형 · 성장");
  }

  @Test
  @DisplayName("많이 해당하는 습관은 평균이 5 보다 높은 것만, 높은 것부터 (모두 딱 5.0 이면 없음)")
  void topHabits() {
    assertThat(StudyStyle.topHabitsOf(all(5.0))).isEmpty();

    Map<CognitiveBias, Double> scores = all(4.0);
    scores.put(CognitiveBias.FUTURE_SELF, 5.3);
    scores.put(CognitiveBias.PLANNING_FALLACY, 6.0);
    scores.put(CognitiveBias.MORAL_LICENSING, 5.0);

    assertThat(StudyStyle.topHabitsOf(scores))
        .containsExactly(CognitiveBias.PLANNING_FALLACY, CognitiveBias.FUTURE_SELF);
  }

  @Test
  @DisplayName("점수가 빠진 습관은 가운데(4)로 본다")
  void missingScoreIsMiddle() {
    assertThat(StudyStyle.styleNameOf(new EnumMap<>(CognitiveBias.class)))
        .isEqualTo("균형 · 균형 · 균형 · 균형");
  }
}
