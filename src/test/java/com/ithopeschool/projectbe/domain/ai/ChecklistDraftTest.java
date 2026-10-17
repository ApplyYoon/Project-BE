package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// GPT 가 돌려준 체크리스트 항목을 검사하는 규칙(#236)을 확인합니다.
class ChecklistDraftTest {

  private static final ChecklistPrompt.Count THREE_TO_SIX = new ChecklistPrompt.Count(3, 6);

  private static List<String> from(ChecklistPrompt.Count count, String... items) {
    return ChecklistDraft.from(new ChecklistPrompt.Answer(Arrays.asList(items)), count);
  }

  @Test
  @DisplayName("주석의 예시: 공백을 지우고, 빈 항목과 같은 항목을 버린다")
  void example() {
    assertThat(from(THREE_TO_SIX, "42~45쪽 풀기", " 오답 표시 ", "", "42~45쪽 풀기", "채점하기"))
        .containsExactly("42~45쪽 풀기", "오답 표시", "채점하기");
  }

  @Test
  @DisplayName("남은 항목이 최소 개수보다 적으면 null 이다 (FE 가 기본값을 씀)")
  void tooFewIsNull() {
    assertThat(from(THREE_TO_SIX, "42~45쪽 풀기", "채점하기")).isNull();
    assertThat(from(THREE_TO_SIX)).isNull();
    assertThat(ChecklistDraft.from(new ChecklistPrompt.Answer(null), THREE_TO_SIX)).isNull();
    assertThat(ChecklistDraft.from(null, THREE_TO_SIX)).isNull();
  }

  @Test
  @DisplayName("많으면 앞에서부터 최대 개수만 남긴다")
  void tooManyIsCut() {
    assertThat(from(new ChecklistPrompt.Count(2, 2), "하나", "둘", "셋")).containsExactly("하나", "둘");
  }

  @Test
  @DisplayName("50자까지는 받고 51자는 버린다. null 항목도 버린다")
  void lengthLimit() {
    String fifty = "가".repeat(50);
    String fiftyOne = "가".repeat(51);

    assertThat(from(new ChecklistPrompt.Count(1, 6), fifty, fiftyOne, null)).containsExactly(fifty);
  }

  @Test
  @DisplayName("깎아내리는 말(금지 표현)이 든 항목은 버린다")
  void bannedExpressionIsDropped() {
    assertThat(from(new ChecklistPrompt.Count(1, 6), "게으르게 굴지 말고 풀기", "5문제 풀기"))
        .containsExactly("5문제 풀기");
  }
}
