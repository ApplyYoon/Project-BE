package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 첫 준비 행동(#236)에서 GPT 에게 주는 글과 답 검사를 확인합니다. GPT 를 부르지 않습니다.
class FirstStepTest {

  @Test
  @DisplayName("단계 0: 카드 제목 · 첫 항목 · 1분 크기만 주고, 앞의 행동 줄은 없다")
  void inputLevelZero() {
    assertThat(FirstStep.input("42~45쪽 문제 풀기", "수학 문제집", 0, null))
        .isEqualTo(
            """
            카드 제목: 수학 문제집
            체크리스트 첫 항목: 42~45쪽 문제 풀기
            크기: 1분 안에 끝나는 준비 행동 하나 (예: "수학 문제집 42쪽 펴기")""");
  }

  @Test
  @DisplayName("단계 1 · 2: 더 작은 크기와 바로 앞에 보여 준 행동을 준다. 카드 제목이 없으면 (없음)")
  void inputSmaller() {
    assertThat(FirstStep.input("42~45쪽 문제 풀기", null, 1, "수학 문제집 42쪽 펴기"))
        .isEqualTo(
            """
            카드 제목: (없음)
            체크리스트 첫 항목: 42~45쪽 문제 풀기
            크기: '바로 앞에 보여 준 행동' 보다 더 작게, 30초 안에 끝나는 것 (예: "수학 문제집 꺼내기")
            바로 앞에 보여 준 행동: 수학 문제집 42쪽 펴기""");
    assertThat(FirstStep.input("42~45쪽 문제 풀기", null, 2, "수학 문제집 꺼내기"))
        .contains("크기: 가장 작게, 10초 안에 몸을 한 번 움직이는 것");
  }

  @Test
  @DisplayName("질문 이름은 first_step, 답 모양은 글 한 칸 step 이고, 지침에 깎아내리지 말라는 규칙이 있다")
  void question() {
    AiQuestion question = FirstStep.question("단어 외우기", null, 0, null);

    assertThat(question.name()).isEqualTo("first_step");
    assertThat(question.schema().get("required")).isEqualTo(List.of("step"));
    assertThat(question.instructions())
        .contains(ToneGuide.NO_PUT_DOWN_RULE)
        .contains("지시가 아니야")
        .contains(FirstStep.MAX_STEP_LENGTH + "자를 넘기지 말고");
  }

  @Test
  @DisplayName("답은 공백을 지워 쓰고, 비었거나 40자를 넘거나 금지 표현이 있거나 앞의 행동과 같으면 null 이다")
  void from() {
    assertThat(FirstStep.from(new FirstStep.Answer(" 수학 문제집 42쪽 펴기 "), null))
        .isEqualTo("수학 문제집 42쪽 펴기");
    assertThat(FirstStep.from(new FirstStep.Answer("가".repeat(40)), null)).hasSize(40);

    assertThat(FirstStep.from(new FirstStep.Answer("가".repeat(41)), null)).isNull();
    assertThat(FirstStep.from(new FirstStep.Answer("  "), null)).isNull();
    assertThat(FirstStep.from(new FirstStep.Answer(null), null)).isNull();
    assertThat(FirstStep.from(null, null)).isNull();
    assertThat(FirstStep.from(new FirstStep.Answer("게으르게 굴지 말고 책 펴기"), null)).isNull();
    assertThat(FirstStep.from(new FirstStep.Answer("수학 문제집 꺼내기"), "수학 문제집 꺼내기")).isNull();
  }
}
