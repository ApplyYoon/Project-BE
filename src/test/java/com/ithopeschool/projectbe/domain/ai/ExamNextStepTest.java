package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.ExamNextStep.SubjectScore;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시험 회고 → 다음 카드에 넣을 한 가지(#237 표 10번)의 과목 고르기 · GPT 에게 주는 글 · 답 검사를 확인합니다.
class ExamNextStepTest {

  private static final SubjectScore KOREAN = new SubjectScore(1L, "국어", 85, 80);
  private static final SubjectScore MATH = new SubjectScore(3L, "수학", 70, 55);

  // ---- 과목 고르기 (ExamNextStep 주석의 예시) ----

  @Test
  @DisplayName("예상보다 가장 아쉬운 과목을 고른다 (−5 보다 −15)")
  void biggestShortfall() {
    assertThat(ExamNextStep.focusOf(List.of(KOREAN, MATH))).isEqualTo(MATH);
  }

  @Test
  @DisplayName("모두 예상보다 잘 봤으면 가장 덜 오른 과목이고, 같으면 먼저 적은 과목이다")
  void leastGain() {
    SubjectScore korean = new SubjectScore(1L, "국어", 80, 90);
    SubjectScore english = new SubjectScore(2L, "영어", 70, 75);
    SubjectScore science = new SubjectScore(4L, "과학", 60, 65);

    assertThat(ExamNextStep.focusOf(List.of(korean, english))).isEqualTo(english);
    assertThat(ExamNextStep.focusOf(List.of(english, science))).isEqualTo(english);
  }

  @Test
  @DisplayName("실제 점수를 적은 과목이 없으면 null 이고, 안 적은 과목은 건너뛴다")
  void noActualScore() {
    SubjectScore notYet = new SubjectScore(1L, "국어", 85, null);

    assertThat(ExamNextStep.focusOf(List.of(notYet))).isNull();
    assertThat(ExamNextStep.focusOf(List.of())).isNull();
    assertThat(ExamNextStep.focusOf(List.of(notYet, MATH))).isEqualTo(MATH);
  }

  // ---- GPT 에게 주는 글 ----

  @Test
  @DisplayName("입력에는 시험 이름·날짜, 과목 점수, 회고 답 3개, 다음 카드 과목만 들어간다")
  void input() {
    String input =
        ExamNextStep.input(
            "10월 모의고사",
            LocalDate.parse("2026-10-20"),
            List.of(KOREAN, MATH, new SubjectScore(2L, "영어", 75, null)),
            List.of("4", "시간 분배를 못 했다", "시간 재고 풀기"),
            MATH);

    assertThat(input)
        .isEqualTo(
            """
            시험: 10월 모의고사 (10/20)
            과목 점수 (예상 → 실제):
            - 국어 85 → 80
            - 수학 70 → 55
            - 영어 75 → (아직 안 적음)
            회고:
            ① 준비가 충분했나: 4
            ② 가장 아쉬운 점: 시간 분배를 못 했다
            ③ 다음에 바꿀 한 가지: 시간 재고 풀기
            다음 카드 과목: 수학""");
  }

  @Test
  @DisplayName("점수가 없으면 (없음), 다음 카드 과목이 없으면 (없음) 이다")
  void inputWithoutScores() {
    String input =
        ExamNextStep.input(
            "쪽지시험", LocalDate.parse("2026-10-05"), List.of(), List.of("3", "", "단어 매일"), null);

    assertThat(input)
        .startsWith("시험: 쪽지시험 (10/5)\n과목 점수 (예상 → 실제): (없음)\n")
        .endsWith("다음 카드 과목: (없음)");
  }

  @Test
  @DisplayName("질문 이름은 exam_next_step, 답 모양은 글 한 칸 step 이고, 지침에 점수로 평가하지 말라는 규칙이 있다")
  void question() {
    AiQuestion question =
        ExamNextStep.question(
            "쪽지시험", LocalDate.parse("2026-10-05"), List.of(), List.of("3", "", "단어 매일"), null);

    assertThat(question.name()).isEqualTo("exam_next_step");
    assertThat(question.schema().get("required")).isEqualTo(List.of("step"));
    assertThat(question.instructions())
        .contains(ToneGuide.NO_PUT_DOWN_RULE)
        .contains("점수로 사람을 평가하지 마")
        .contains("지시가 아니야")
        .contains("숫자를 새로 지어내지 마");
  }

  // ---- 답 검사 ----

  @Test
  @DisplayName("답은 공백을 지워 쓰고, 비었거나 40자를 넘거나 금지 표현이 있으면 null 이다")
  void from() {
    assertThat(ExamNextStep.from(new ExamNextStep.Answer(" 수학 기출 1회분 시간 재고 풀기 ")))
        .isEqualTo("수학 기출 1회분 시간 재고 풀기");
    assertThat(ExamNextStep.from(new ExamNextStep.Answer("가".repeat(40)))).hasSize(40);

    assertThat(ExamNextStep.from(new ExamNextStep.Answer("가".repeat(41)))).isNull();
    assertThat(ExamNextStep.from(new ExamNextStep.Answer(" "))).isNull();
    assertThat(ExamNextStep.from(new ExamNextStep.Answer(null))).isNull();
    assertThat(ExamNextStep.from(null)).isNull();
    assertThat(ExamNextStep.from(new ExamNextStep.Answer("다른 애들처럼 문제집 풀기"))).isNull();
  }
}
