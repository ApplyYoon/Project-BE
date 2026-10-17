package com.ithopeschool.projectbe.domain.cardday;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 사후 입력 마감("그날, 다음 통화 전까지")을 LateEntryRule 주석의 예시 그대로 확인합니다. (이슈 #225)
class LateEntryRuleTest {

  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);
  // 10-12 19:00 시작 카드
  private static final Instant CARD_START = kst(12, 19, 0);

  // 한국 시간 2026년 10월의 순간입니다.
  private static Instant kst(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
  }

  @Test
  @DisplayName("카드 시간이 지난 뒤, 그날 통화가 없으면 받는다 (밤 3시 30분도 아직 그날)")
  void openWithoutCall() {
    assertThat(LateEntryRule.isOpen(OCT_12, CARD_START, kst(12, 20, 30), List.of())).isTrue();
    assertThat(LateEntryRule.isOpen(OCT_12, CARD_START, kst(13, 3, 30), List.of())).isTrue();
  }

  @Test
  @DisplayName("카드 시작 뒤에 통화가 있었으면 마감, 카드 전의 통화는 상관없다")
  void closedAfterCall() {
    assertThat(LateEntryRule.isOpen(OCT_12, CARD_START, kst(12, 22, 0), List.of(kst(12, 21, 0))))
        .isFalse();
    assertThat(LateEntryRule.isOpen(OCT_12, CARD_START, kst(12, 22, 0), List.of(kst(12, 8, 0))))
        .isTrue();
  }

  @Test
  @DisplayName("날이 바뀌면(다음 날 04:00) 마감, 카드 시간 전에는 아직 사후가 아니라서 받지 않는다")
  void closedOutsideTheDay() {
    assertThat(LateEntryRule.isOpen(OCT_12, CARD_START, kst(13, 4, 0), List.of())).isFalse();
    assertThat(LateEntryRule.isOpen(OCT_12, CARD_START, kst(12, 18, 0), List.of())).isFalse();
  }
}
