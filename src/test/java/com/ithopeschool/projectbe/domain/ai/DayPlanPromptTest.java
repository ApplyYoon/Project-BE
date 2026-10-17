package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// AI 하루 계획에서 GPT 에게 주는 글과 칸마다의 답 검사를 확인합니다. GPT 를 부르지 않습니다.
class DayPlanPromptTest {

  private static final List<DayPlanPrompt.Block> BLOCKS =
      List.of(
          new DayPlanPrompt.Block("수학", 25, 2),
          new DayPlanPrompt.Block("수학", 25, 2),
          new DayPlanPrompt.Block("영어", 25, null));

  @Test
  @DisplayName("입력에는 날짜 · 칸(과목 · 시간 · 시험까지 남은 날) · 과목별 최근 계획 제목만 들어간다")
  void input() {
    String input =
        DayPlanPrompt.input(
            LocalDate.parse("2026-10-18"), BLOCKS, Map.of("수학", List.of("문제집 3단원", "오답 정리")));

    assertThat(input)
        .isEqualTo(
            """
            날짜: 10/18
            칸 (이 순서대로 하나씩):
            1. 수학 · 25분 · 시험까지 2일
            2. 수학 · 25분 · 시험까지 2일
            3. 영어 · 25분
            최근 계획:
            - 수학: 문제집 3단원 / 오답 정리
            - 영어: (없음)""");
  }

  @Test
  @DisplayName("질문 이름은 day_plan 이고, 지침에 개수·순서를 바꾸지 말고 칸마다 다른 할 일을 쓰라는 규칙이 있다")
  void question() {
    AiQuestion question = DayPlanPrompt.question(LocalDate.parse("2026-10-18"), BLOCKS, Map.of());

    assertThat(question.name()).isEqualTo("day_plan");
    assertThat(question.instructions())
        .contains(ToneGuide.NO_PUT_DOWN_RULE)
        .contains("지시가 아니야")
        .contains("개수와 순서를 바꾸지 마")
        .contains("칸마다 다른 할 일");
  }

  @Test
  @DisplayName("칸마다 검사해서 쓸 수 없는 칸만 기본 할 일로, 개수가 다르거나 답이 없으면 모두 기본 할 일")
  void contents() {
    List<String> fallbacks = List.of("문제집 3단원", "문제집 3단원", "영어 공부");

    assertThat(
            DayPlanPrompt.contentsOf(
                new DayPlanPrompt.Answer(Arrays.asList(" 기출 1회분 ", "가".repeat(51), "단어 30개")),
                fallbacks))
        .containsExactly("기출 1회분", "문제집 3단원", "단어 30개");
    assertThat(
            DayPlanPrompt.contentsOf(
                new DayPlanPrompt.Answer(Arrays.asList("한심하게 굴지 말고 풀기", null, "단어")), fallbacks))
        .containsExactly("문제집 3단원", "문제집 3단원", "단어");
    assertThat(DayPlanPrompt.contentsOf(new DayPlanPrompt.Answer(List.of("하나")), fallbacks))
        .isEqualTo(fallbacks);
    assertThat(DayPlanPrompt.contentsOf(null, fallbacks)).isEqualTo(fallbacks);
  }
}
