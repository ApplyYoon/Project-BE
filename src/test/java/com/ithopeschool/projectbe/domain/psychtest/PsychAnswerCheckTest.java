package com.ithopeschool.projectbe.domain.psychtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 성향 검사의 답이 믿을 만한지(PsychAnswerCheck) 봅니다. FE isPsychTestReliable 의 예시와 같습니다.
class PsychAnswerCheckTest {

  // 화면 순서(S1 A1 P1 R1 F1 E1 C1 → S2 …)대로 답 35개를 넣습니다.
  private static Map<PsychItem, Integer> shownAnswers(List<Integer> shown) {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    int index = 0;
    for (int number = 1; number <= 5; number++) {
      for (PsychScale scale : PsychScale.values()) {
        for (PsychItem item : PsychItem.values()) {
          if (item.scale() == scale && item.number() == number) {
            answers.put(item, shown.get(index++));
          }
        }
      }
    }
    return answers;
  }

  // 모든 문장에 같은 답
  private static Map<PsychItem, Integer> allAnswers(int value) {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      answers.put(item, value);
    }
    return answers;
  }

  @Test
  @DisplayName("화면 순서는 번호 순서, 같은 번호 안에서는 척도 순서다 (S1 A1 P1 R1 F1 E1 C1 → S2 …)")
  void shownOrder() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    answers.put(PsychItem.S2, 3);
    answers.put(PsychItem.C1, 2);
    answers.put(PsychItem.S1, 1);
    assertThat(PsychAnswerCheck.inShownOrder(answers)).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("35개가 모두 같거나, 32개(90% 이상)가 같으면 믿기 어렵다")
  void sameAnswers() {
    assertThat(PsychAnswerCheck.isReliable(allAnswers(4))).isFalse();

    Map<PsychItem, Integer> almost = allAnswers(4);
    almost.put(PsychItem.S1, 6);
    almost.put(PsychItem.A2, 2);
    almost.put(PsychItem.C3, 5);
    // 35개 중 32개가 4 → 91%
    assertThat(PsychAnswerCheck.isReliable(almost)).isFalse();

    // 31개(89%)면 아직 믿습니다. ("대부분 보통" 인 학생도 있어서)
    almost.put(PsychItem.E4, 6);
    assertThat(PsychAnswerCheck.isReliable(almost)).isTrue();
  }

  @Test
  @DisplayName("화면 순서대로 1-7-1-7 이나 1-4-7-1-4-7 을 되풀이했으면 믿기 어렵다")
  void repeated() {
    List<Integer> alternating = new java.util.ArrayList<>();
    List<Integer> threeCycle = new java.util.ArrayList<>();
    for (int i = 0; i < 35; i++) {
      alternating.add(i % 2 == 0 ? 1 : 7);
      threeCycle.add(List.of(1, 4, 7).get(i % 3));
    }
    assertThat(PsychAnswerCheck.isReliable(shownAnswers(alternating))).isFalse();
    assertThat(PsychAnswerCheck.isReliable(shownAnswers(threeCycle))).isFalse();
  }

  @Test
  @DisplayName("척도마다 생각이 뚜렷해서 7칸마다 같은 답이 나오는 학생은 믿는다")
  void consistentByScale() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    // 시작 저항 6 · 주의 분산 2 · 계획 낙관 5 · 기록 부담 3 · 실패 민감도 7 · 외부 귀인 1 · 관리 선호 4
    Map<PsychScale, Integer> byScale = new EnumMap<>(PsychScale.class);
    int[] values = {6, 2, 5, 3, 7, 1, 4};
    for (PsychScale scale : PsychScale.values()) {
      byScale.put(scale, values[scale.ordinal()]);
    }
    for (PsychItem item : PsychItem.values()) {
      answers.put(item, byScale.get(item.scale()));
    }
    assertThat(PsychAnswerCheck.isReliable(answers)).isTrue();
  }

  @Test
  @DisplayName("고르게 섞인 보통의 답, 한 척도만 높은 답, 옛 14문항 검사의 보통 답은 믿는다")
  void normalAnswers() {
    List<Integer> mixed =
        List.of(
            5, 3, 6, 2, 4, 4, 5, 6, 2, 5, 3, 4, 5, 3, 4, 3, 6, 2, 5, 4, 6, 5, 2, 4, 3, 4, 5, 3, 6,
            3, 5, 2, 4, 5, 4);
    assertThat(PsychAnswerCheck.isReliable(shownAnswers(mixed))).isTrue();

    // 시작 저항만 7, 나머지 4 → 4 가 30개(86%)
    Map<PsychItem, Integer> oneScale = allAnswers(4);
    for (PsychItem item : PsychItem.values()) {
      if (item.scale() == PsychScale.START_RESISTANCE) {
        oneScale.put(item, 7);
      }
    }
    assertThat(PsychAnswerCheck.isReliable(oneScale)).isTrue();

    Map<PsychItem, Integer> old = new EnumMap<>(PsychItem.class);
    int[] oldValues = {6, 3, 5, 2, 4, 5, 3, 5, 2, 6, 3, 4, 4, 2};
    int index = 0;
    for (PsychItem item : PsychItem.values()) {
      if (item.isInOldTest()) {
        old.put(item, oldValues[index++]);
      }
    }
    assertThat(PsychAnswerCheck.isReliable(old)).isTrue();
    assertThat(PsychAnswerCheck.isReliable(Map.of())).isTrue();
  }
}
