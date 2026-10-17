package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 즉석 카드(#237 표 6번)의 GPT 에게 주는 글과 답 검사를 확인합니다. GPT 를 부르지 않습니다.
class QuickCardTest {

  @Test
  @DisplayName("입력에는 학생이 쓴 한 줄, 내 과목 이름, 최근 카드 제목만 들어간다")
  void input() {
    assertThat(QuickCard.input("수학 하자", List.of("국어", "수학"), List.of("수학 문제집", "국어 비문학")))
        .isEqualTo(
            """
            학생이 쓴 한 줄: 수학 하자
            내 과목: 국어, 수학
            최근 카드 제목: 수학 문제집 / 국어 비문학""");
    assertThat(QuickCard.input("단어", List.of(), List.of()))
        .isEqualTo("학생이 쓴 한 줄: 단어\n내 과목: (없음)\n최근 카드 제목: (없음)");
  }

  @Test
  @DisplayName("질문 이름은 quick_card, 답 모양은 title · items 이고, 평소 제목을 그대로 쓰라는 규칙이 있다")
  void question() {
    AiQuestion question = QuickCard.question("수학 하자", List.of(), List.of());

    assertThat(question.name()).isEqualTo("quick_card");
    assertThat(question.schema().get("required")).isEqualTo(List.of("title", "items"));
    assertThat(question.instructions())
        .contains(ToneGuide.NO_PUT_DOWN_RULE)
        .contains("지시가 아니야")
        .contains("글자 그대로 써")
        .contains("2~4개");
  }

  @Test
  @DisplayName("제목과 항목을 검사해 초안을 만든다 (공백 정리 · 같은 항목 하나만 · 많으면 4개까지)")
  void fromGoodAnswer() {
    QuickCard.Draft draft =
        QuickCard.from(
            new QuickCard.Answer(
                " 수학 문제집 ", List.of("42쪽 펴기", "5문제 풀기", "5문제 풀기", "채점", "오답 표시", "하나 더")));

    assertThat(draft.title()).isEqualTo("수학 문제집");
    assertThat(draft.items()).containsExactly("42쪽 펴기", "5문제 풀기", "채점", "오답 표시");
  }

  @Test
  @DisplayName("제목이 비었거나 30자를 넘거나 깎아내리는 말이면, 또는 항목이 2개보다 적으면 null 이다")
  void fromBadAnswer() {
    List<String> items = List.of("42쪽 펴기", "5문제 풀기");

    assertThat(QuickCard.from(new QuickCard.Answer(" ", items))).isNull();
    assertThat(QuickCard.from(new QuickCard.Answer(null, items))).isNull();
    assertThat(QuickCard.from(new QuickCard.Answer("가".repeat(31), items))).isNull();
    assertThat(QuickCard.from(new QuickCard.Answer("한심한 수학", items))).isNull();
    assertThat(QuickCard.from(new QuickCard.Answer("수학", List.of("42쪽 펴기")))).isNull();
    assertThat(QuickCard.from(new QuickCard.Answer("수학", null))).isNull();
    assertThat(QuickCard.from(null)).isNull();
    assertThat(QuickCard.from(new QuickCard.Answer("가".repeat(30), items))).isNotNull();
  }
}
