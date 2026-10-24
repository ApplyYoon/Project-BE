package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 관리 4단계 규칙을 Stage4 주석의 예시 그대로 확인합니다. (이슈 #234, FE src/lib/stage4.ts)
class Stage4Test {

  private static final Long ME = 1L;
  private static final LocalDate OCT_17 = LocalDate.of(2026, 10, 17);
  private static final List<Integer> EVERY_DAY = List.of(0, 1, 2, 3, 4, 5, 6);

  private static Instant kst(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
  }

  // 10/01 부터 repeatDays 요일마다 하는 카드 (repeatDays 가 비어 있으면 10/17 하루만 하는 1회성 카드)
  private static Card card(long id, List<Integer> repeatDays, int hour) {
    LocalDate date = repeatDays.isEmpty() ? OCT_17 : LocalDate.of(2026, 10, 1);
    Card card = new Card(ME, "수학", date, 60, List.of(new ChecklistItem("i1", "42쪽")), kst(1, 9, 0));
    card.changeRepeatDays(repeatDays);
    card.changeStartTime(LocalTime.of(hour, 0));
    ReflectionTestUtils.setField(card, "id", id);
    return card;
  }

  @Test
  @DisplayName("4단계 장치는 4단계 · 안 풀림 · 한도 4 · 보호 조치 꺼짐일 때만 켜진다")
  void isOn() {
    assertThat(Stage4.isOn(4, false, 4, false)).isTrue();
    // 풀었으면 3단계로 내려와서 꺼짐
    assertThat(Stage4.isOn(4, true, 4, false)).isFalse();
    // 판정 뒤에 한도를 3 으로 낮춤
    assertThat(Stage4.isOn(4, false, 3, false)).isFalse();
    // 안전 브레이크(보호 조치)가 먼저
    assertThat(Stage4.isOn(4, false, 4, true)).isFalse();
    assertThat(Stage4.isOn(3, false, 4, false)).isFalse();
  }

  @Test
  @DisplayName("지금 18:00 이면 가장 가까운 반복 카드(19:00 A)의 오늘 것을 고른다")
  void pickNearestToday() {
    Card a = card(1L, EVERY_DAY, 19);
    Card b = card(2L, EVERY_DAY, 20);

    // 번호 순서와 상관없이 시각이 빠른 카드입니다.
    assertThat(Stage4.pickForcedCard(List.of(b, a), OCT_17, kst(17, 18, 0), Set.of()))
        .isEqualTo(new Stage4.Target(1L, OCT_17));
  }

  @Test
  @DisplayName("오늘 카드 시각이 지났거나 오늘 이미 한 카드면 내일 것을 고른다")
  void pickTomorrow() {
    Card a = card(1L, EVERY_DAY, 19);

    assertThat(Stage4.pickForcedCard(List.of(a), OCT_17, kst(17, 21, 0), Set.of()))
        .isEqualTo(new Stage4.Target(1L, OCT_17.plusDays(1)));
    assertThat(Stage4.pickForcedCard(List.of(a), OCT_17, kst(17, 18, 0), Set.of(1L)))
        .isEqualTo(new Stage4.Target(1L, OCT_17.plusDays(1)));
  }

  @Test
  @DisplayName("1회성 카드 · 이미 절대인 카드는 고르지 않고, 7일 안에 반복 카드가 없으면 null")
  void pickNothing() {
    Card oneOff = card(1L, List.of(), 19);
    Card absolute = card(2L, EVERY_DAY, 19);
    absolute.changePriority(CardPriority.ABSOLUTE);

    assertThat(Stage4.pickForcedCard(List.of(oneOff, absolute), OCT_17, kst(17, 18, 0), Set.of()))
        .isNull();
    assertThat(Stage4.pickForcedCard(List.of(), OCT_17, kst(17, 18, 0), Set.of())).isNull();
  }
}
