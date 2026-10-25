package com.ithopeschool.projectbe.domain.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 카드의 "그 날짜에 하는 카드인지" 규칙과 반복 요일 글자 바꾸기를 확인합니다. DB 없이 돌아갑니다.
// 예시는 FE 의 isCardOnDate (src/lib/cards.ts) 주석과 똑같이 맞췄습니다. 한쪽이 바뀌면 이 테스트로 알 수 있습니다.
class CardTest {

  // 2026-10-12 는 월요일입니다.
  private static final LocalDate MON_OCT_12 = LocalDate.of(2026, 10, 12);
  private static final LocalDate TUE_OCT_13 = LocalDate.of(2026, 10, 13);

  private static Card card(LocalDate date, List<Integer> repeatDays) {
    Card card =
        new Card(
            1L,
            "수학 문제집",
            date,
            60,
            List.of(new ChecklistItem("i1", "42쪽")),
            Instant.parse("2026-10-01T00:00:00Z"));
    card.changeRepeatDays(repeatDays);
    return card;
  }

  @Test
  @DisplayName("1회성 카드는 카드 날짜와 같은 날만 한다")
  void oneOffCard() {
    Card card = card(MON_OCT_12, List.of());

    assertThat(card.isOneOff()).isTrue();
    assertThat(card.isOnDate(MON_OCT_12)).isTrue();
    assertThat(card.isOnDate(TUE_OCT_13)).isFalse();
  }

  @Test
  @DisplayName("반복 카드(10-01 시작, 월·수)는 10-12(월) 에 하고 10-13(화) 에는 안 한다")
  void repeatCardOnWeekday() {
    Card card = card(LocalDate.of(2026, 10, 1), List.of(1, 3));

    assertThat(card.isOneOff()).isFalse();
    assertThat(card.isOnDate(MON_OCT_12)).isTrue();
    assertThat(card.isOnDate(TUE_OCT_13)).isFalse();
  }

  @Test
  @DisplayName("반복 카드는 시작한 날 전에는 안 한다 (10-20 시작 카드는 10-12 에 false)")
  void repeatCardBeforeStart() {
    Card card = card(LocalDate.of(2026, 10, 20), List.of(1, 3));

    assertThat(card.isOnDate(MON_OCT_12)).isFalse();
    // 시작한 날이 그 요일이면 그날부터 합니다. (2026-10-19 는 월요일)
    assertThat(card(LocalDate.of(2026, 10, 19), List.of(1)).isOnDate(LocalDate.of(2026, 10, 19)))
        .isTrue();
  }

  @Test
  @DisplayName("반복 카드는 그만하는 날(endDate)까지만 한다. 그날은 하고, 그 뒤 같은 요일은 안 한다")
  void repeatCardUntilEndDate() {
    // 10-01 시작, 월·수, 10-12(월) 까지
    Card card = card(LocalDate.of(2026, 10, 1), List.of(1, 3));
    card.changeEndDate(MON_OCT_12);

    assertThat(card.isOnDate(LocalDate.of(2026, 10, 7))).isTrue(); // 수요일, 끝나기 전
    assertThat(card.isOnDate(MON_OCT_12)).isTrue(); // 그만하는 날 당일
    assertThat(card.isOnDate(LocalDate.of(2026, 10, 14))).isFalse(); // 수요일, 끝난 뒤
    assertThat(card.isOnDate(LocalDate.of(2026, 10, 19))).isFalse(); // 월요일, 끝난 뒤

    // 다시 null 로 두면 끝 없이 반복합니다.
    card.changeEndDate(null);
    assertThat(card.isOnDate(LocalDate.of(2026, 10, 19))).isTrue();
  }

  @Test
  @DisplayName("1회성 카드는 endDate 를 보지 않는다 (카드 날짜에 한다)")
  void oneOffCardIgnoresEndDate() {
    Card card = card(MON_OCT_12, List.of());
    card.changeEndDate(LocalDate.of(2026, 10, 1));

    assertThat(card.isOnDate(MON_OCT_12)).isTrue();
  }

  @Test
  @DisplayName("요일 번호는 FE 와 같다: 일요일 0, 월요일 1, 토요일 6")
  void weekdayNumbers() {
    assertThat(Card.weekdayOf(LocalDate.of(2026, 10, 11))).isZero();
    assertThat(Card.weekdayOf(MON_OCT_12)).isEqualTo(1);
    assertThat(Card.weekdayOf(LocalDate.of(2026, 10, 17))).isEqualTo(6);
  }

  @Test
  @DisplayName("반복 요일은 작은 숫자부터 \"1,3,5\" 로 적고, 비어 있으면 null(1회성) 이다")
  void repeatDaysText() {
    assertThat(Card.toRepeatDaysText(List.of(5, 1, 3))).isEqualTo("1,3,5");
    assertThat(Card.toRepeatDaysText(List.of())).isNull();
    assertThat(Card.toRepeatDaysText(null)).isNull();

    Card card = card(MON_OCT_12, List.of(6, 0));
    assertThat(card.getRepeatDays()).isEqualTo("0,6");
    assertThat(card.repeatDayList()).containsExactly(0, 6);
  }

  @Test
  @DisplayName("새 카드는 유연 · 자습으로 시작한다")
  void defaults() {
    Card card = card(MON_OCT_12, List.of());

    assertThat(card.getPriority()).isEqualTo(CardPriority.FLEXIBLE);
    assertThat(card.getType()).isEqualTo(CardType.SELF);
    assertThat(card.getAlertMethod()).isNull();
  }
}
