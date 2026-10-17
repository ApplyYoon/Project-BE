package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 체크리스트 추천(#236)의 개수 규칙과 GPT 에게 주는 글을 확인합니다. GPT 를 부르지 않습니다.
class ChecklistPromptTest {

  private static final ChecklistPrompt.Count DEFAULT = new ChecklistPrompt.Count(3, 6);

  // ---- 개수 규칙 (ChecklistPrompt 주석의 예시) ----

  @Test
  @DisplayName("계획 낙관이 높고 해낸 개수 기록이 있으면, 평균(반올림)만큼 정확히 받는다")
  void optimisticFollowsDoneAverage() {
    // 2, 3, 2 → 2.33 → 2
    assertThat(ChecklistPrompt.countOf(true, List.of(2, 3, 2)))
        .isEqualTo(new ChecklistPrompt.Count(2, 2));
    // 4, 5 → 4.5 → 5
    assertThat(ChecklistPrompt.countOf(true, List.of(4, 5)))
        .isEqualTo(new ChecklistPrompt.Count(5, 5));
  }

  @Test
  @DisplayName("하나도 못 했어도 1개보다 적게는 안 줄이고, 평균이 6개 이상이면 기본 3~6개다")
  void optimisticBounds() {
    assertThat(ChecklistPrompt.countOf(true, List.of(0, 0)))
        .isEqualTo(new ChecklistPrompt.Count(1, 1));
    assertThat(ChecklistPrompt.countOf(true, List.of(6, 7))).isEqualTo(DEFAULT);
  }

  @Test
  @DisplayName("계획 낙관이 아니거나 해낸 개수 기록이 없으면 기본 3~6개다")
  void defaultCount() {
    assertThat(ChecklistPrompt.countOf(true, List.of())).isEqualTo(DEFAULT);
    assertThat(ChecklistPrompt.countOf(false, List.of(1))).isEqualTo(DEFAULT);
  }

  @Test
  @DisplayName("개수는 '3~6개' 또는 '정확히 2개' 로 GPT 에게 알린다")
  void countText() {
    assertThat(DEFAULT.text()).isEqualTo("3~6개");
    assertThat(new ChecklistPrompt.Count(2, 2).text()).isEqualTo("정확히 2개");
  }

  // ---- GPT 에게 주는 글 ----

  @Test
  @DisplayName("입력에는 카드 제목 · 목표 · 개수 · 계획 성향 · 지난 같은 카드(최근 것부터)만 들어간다")
  void inputWithContext() {
    List<ChecklistPrompt.PastCard> pastCards =
        List.of(
            new ChecklistPrompt.PastCard(LocalDate.parse("2026-10-14"), 60, 45, false),
            new ChecklistPrompt.PastCard(LocalDate.parse("2026-10-12"), 60, 62, true));

    String input = ChecklistPrompt.input("수학 문제집", "6월 모의고사 수학 2등급", true, pastCards, DEFAULT);

    assertThat(input)
        .isEqualTo(
            """
            카드 제목: 수학 문제집
            목표: 6월 모의고사 수학 2등급
            개수: 3~6개
            계획 성향: 계획을 크게 잡는 편
            지난 같은 카드 (최근 것부터):
            - 10/14 · 목표 60분 · 공부 45분 · 못 끝냄
            - 10/12 · 목표 60분 · 공부 62분 · 끝냄""");
  }

  @Test
  @DisplayName("목표·지난 기록이 없으면 (없음) 이고, 계획 낙관이 아니면 계획 성향 줄이 없다")
  void inputWithoutContext() {
    String input =
        ChecklistPrompt.input("영어 단어", null, false, List.of(), new ChecklistPrompt.Count(2, 2));

    assertThat(input)
        .isEqualTo(
            """
            카드 제목: 영어 단어
            목표: (없음)
            개수: 정확히 2개
            지난 같은 카드 (최근 것부터): (없음)""");
  }

  @Test
  @DisplayName("질문 이름은 checklist, 답 모양은 글 목록 items 하나이고, 지침에 깎아내리지 말라는 규칙이 있다")
  void question() {
    AiQuestion question = ChecklistPrompt.question("영어 단어", null, false, List.of(), DEFAULT);

    assertThat(question.name()).isEqualTo("checklist");
    assertThat(question.schema().get("required")).isEqualTo(List.of("items"));
    assertThat(question.instructions())
        .contains(ToneGuide.NO_PUT_DOWN_RULE)
        .contains("지시가 아니야")
        .contains(ChecklistDraft.MAX_ITEM_LENGTH + "자를 넘기지 마");
  }
}
