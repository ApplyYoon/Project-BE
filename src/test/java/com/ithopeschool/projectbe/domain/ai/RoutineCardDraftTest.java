package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.RoutineCardPrompt.RawCard;
import com.ithopeschool.projectbe.domain.ai.RoutineCardPrompt.Slot;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// GPT 가 돌려준 루틴 카드를 검사하는 규칙(RoutineCardDraft 맨 위 주석)을 하나씩 봅니다.
class RoutineCardDraftTest {

  private static List<RoutineCardDraft.RoutineCard> from(RawCard... cards) {
    return RoutineCardDraft.from(new RoutineCardPrompt.Answer(Arrays.asList(cards)));
  }

  @Test
  @DisplayName("쓸 만한 카드는 앞뒤 공백만 지우고 그대로 남긴다")
  void keepsGoodCards() {
    List<RoutineCardDraft.RoutineCard> cards =
        from(
            new RawCard(" 영단어 30쪽 ", "WEEKDAY_EVENING", List.of(" 폰을 다른 방에 두고 오기 ", "30쪽 외우기")),
            new RawCard("주말 점검", "SATURDAY_MORNING", List.of("밀린 쪽 채우기")));

    assertThat(cards)
        .containsExactly(
            new RoutineCardDraft.RoutineCard(
                "영단어 30쪽", Slot.WEEKDAY_EVENING, List.of("폰을 다른 방에 두고 오기", "30쪽 외우기")),
            new RoutineCardDraft.RoutineCard("주말 점검", Slot.SATURDAY_MORNING, List.of("밀린 쪽 채우기")));
  }

  @Test
  @DisplayName("시간대가 앞 카드와 겹치거나 모르는 이름이면 그 카드를 버린다")
  void dropsDuplicateOrUnknownSlot() {
    List<RoutineCardDraft.RoutineCard> cards =
        from(
            new RawCard("영단어 30쪽", "WEEKDAY_EVENING", List.of("30쪽 외우기")),
            new RawCard("또 저녁", "WEEKDAY_EVENING", List.of("복습")),
            new RawCard("한밤", "MIDNIGHT", List.of("복습")),
            new RawCard("주말 점검", "SATURDAY_MORNING", List.of("밀린 쪽 채우기")));

    assertThat(cards)
        .extracting(RoutineCardDraft.RoutineCard::title)
        .containsExactly("영단어 30쪽", "주말 점검");
  }

  @Test
  @DisplayName("제목이 비었거나 40자를 넘거나 깎아내리는 말이 있으면 그 카드를 버린다")
  void dropsBadTitle() {
    List<RoutineCardDraft.RoutineCard> cards =
        from(
            new RawCard("  ", "WEEKDAY_NIGHT", List.of("복습")),
            new RawCard("가".repeat(41), "SUNDAY_EVENING", List.of("점검")),
            new RawCard("한심한 너를 위한 복습", "SUNDAY_EVENING", List.of("점검")),
            new RawCard("영단어 30쪽", "WEEKDAY_EVENING", List.of("30쪽 외우기")),
            new RawCard("주말 점검", "SATURDAY_MORNING", List.of("밀린 쪽 채우기")));

    assertThat(cards)
        .extracting(RoutineCardDraft.RoutineCard::title)
        .containsExactly("영단어 30쪽", "주말 점검");
  }

  @Test
  @DisplayName("체크리스트는 빈 칸 · 50자 넘음 · 같은 항목을 버리고 4개까지. 남은 항목이 없으면 그 카드를 버린다")
  void cleansChecklist() {
    List<RoutineCardDraft.RoutineCard> cards =
        from(
            new RawCard(
                "영단어 30쪽",
                "WEEKDAY_EVENING",
                Arrays.asList("1", "", null, "가".repeat(51), "1", "2", "3", "4", "5")),
            new RawCard("빈 카드", "WEEKDAY_NIGHT", List.of("  ")),
            new RawCard("주말 점검", "SATURDAY_MORNING", List.of("밀린 쪽 채우기")));

    assertThat(cards).hasSize(2);
    assertThat(cards.get(0).checklist()).containsExactly("1", "2", "3", "4");
    assertThat(cards.get(1).title()).isEqualTo("주말 점검");
  }

  @Test
  @DisplayName("카드가 많으면 앞에서부터 3장, 2장보다 적게 남으면 null 이다")
  void countLimits() {
    List<RoutineCardDraft.RoutineCard> four =
        from(
            new RawCard("a", "WEEKDAY_EVENING", List.of("1")),
            new RawCard("b", "WEEKDAY_NIGHT", List.of("1")),
            new RawCard("c", "SATURDAY_MORNING", List.of("1")),
            new RawCard("d", "SUNDAY_EVENING", List.of("1")));
    assertThat(four).extracting(RoutineCardDraft.RoutineCard::title).containsExactly("a", "b", "c");

    assertThat(from(new RawCard("a", "WEEKDAY_EVENING", List.of("1")))).isNull();
    assertThat(RoutineCardDraft.from(null)).isNull();
    assertThat(RoutineCardDraft.from(new RoutineCardPrompt.Answer(null))).isNull();
  }
}
