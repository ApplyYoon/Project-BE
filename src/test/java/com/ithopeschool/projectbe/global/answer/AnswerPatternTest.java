package com.ithopeschool.projectbe.global.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 답 모양 검사 도구(AnswerPattern)가 주석의 예시대로 계산하는지 봅니다.
class AnswerPatternTest {

  @Test
  @DisplayName("가장 많은 답의 비율: 4 가 4개 중 3개면 0.75, 답이 없으면 0")
  void topShare() {
    assertThat(AnswerPattern.topShare(List.of(4, 4, 4, 5))).isEqualTo(0.75);
    assertThat(AnswerPattern.topShare(List.of(1, 2, 3, 4))).isEqualTo(0.25);
    assertThat(AnswerPattern.topShare(List.of())).isZero();
  }

  @Test
  @DisplayName("다 같음: 모두 같으면 true, 하나라도 다르거나 답이 하나뿐이면 false")
  void allSame() {
    assertThat(AnswerPattern.isAllSame(List.of(3, 3, 3))).isTrue();
    assertThat(AnswerPattern.isAllSame(List.of(3, 3, 4))).isFalse();
    assertThat(AnswerPattern.isAllSame(List.of(3))).isFalse();
  }

  @Test
  @DisplayName("몇 칸 앞과 같은 답의 비율: 1-2-1-2 는 두 칸 앞과 늘 같고 바로 앞과는 늘 다르다")
  void repeatShare() {
    List<Integer> alternating = List.of(1, 2, 1, 2, 1, 2);
    assertThat(AnswerPattern.repeatShare(alternating, 2)).isEqualTo(1.0);
    assertThat(AnswerPattern.repeatShare(alternating, 1)).isZero();
    // 비교할 쌍이 없으면 0
    assertThat(AnswerPattern.repeatShare(List.of(1, 2), 2)).isZero();
    assertThat(AnswerPattern.repeatShare(alternating, 0)).isZero();
  }

  @Test
  @DisplayName("되풀이: 1-2-1-2 · 1-4-7-1-4-7 · 1-1-7-7 묶음은 true")
  void repeating() {
    assertThat(AnswerPattern.isRepeating(List.of(1, 2, 1, 2, 1, 2, 1, 2), 4, 0.9)).isTrue();
    assertThat(AnswerPattern.isRepeating(List.of(1, 4, 7, 1, 4, 7, 1, 4, 7), 4, 0.9)).isTrue();
    assertThat(AnswerPattern.isRepeating(List.of(1, 1, 7, 7, 1, 1, 7, 7, 1, 1, 7, 7), 4, 0.9))
        .isTrue();
  }

  @Test
  @DisplayName("되풀이가 아님: 다 같은 답 · 고르게 섞인 답 · 묶음이 두 번만 돈 답 · maxCycle 보다 긴 묶음")
  void notRepeating() {
    // 다 같은 답은 되풀이가 아니라 topShare 로 봅니다.
    assertThat(AnswerPattern.isRepeating(List.of(4, 4, 4, 4, 4, 4), 4, 0.9)).isFalse();
    assertThat(AnswerPattern.isRepeating(List.of(5, 3, 6, 2, 4, 4, 7, 1, 3, 5), 4, 0.9)).isFalse();
    // 묶음이 세 번은 돌아야 봅니다. (두 번은 우연히도 나옴)
    assertThat(AnswerPattern.isRepeating(List.of(1, 2, 1, 2), 4, 0.9)).isFalse();
    // 5칸 묶음은 maxCycle 4 로는 보지 않습니다.
    assertThat(
            AnswerPattern.isRepeating(List.of(1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5), 4, 0.9))
        .isFalse();
  }

  @Test
  @DisplayName("몇 개만 실수로 다르게 눌러도 비율이 기준 이상이면 되풀이다")
  void repeatingWithSlip() {
    // 2칸 앞과 비교 10쌍 중 9쌍 같음 = 0.9
    List<Integer> withSlip = List.of(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 3, 2);
    assertThat(AnswerPattern.repeatShare(withSlip, 2)).isEqualTo(0.9);
    assertThat(AnswerPattern.isRepeating(withSlip, 4, 0.9)).isTrue();
  }
}
