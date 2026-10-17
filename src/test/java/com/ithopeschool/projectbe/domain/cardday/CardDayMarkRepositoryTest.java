package com.ithopeschool.projectbe.domain.cardday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ithopeschool.projectbe.domain.care.MissReason;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// CardDayMarkRepository 가 진짜 DB(H2)에서 "내 것, 그 카드, 그 달" 을 정확히 세는지,
// 같은 카드 · 같은 날짜에 두 번 표시하지 못하는지 확인합니다. (이슈 #225)
@DataJpaTest
class CardDayMarkRepositoryTest {

  @Autowired private CardDayMarkRepository repository;
  @Autowired private TestEntityManager em;

  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

  private CardDayMark postponed(Long userId, Long cardId, LocalDate date) {
    return em.persistAndFlush(
        CardDayMark.postponed(
            userId, cardId, date, "이유", date.plusDays(1), LocalTime.of(20, 0), false, NOW));
  }

  @Test
  @DisplayName("이번 달 미룬 횟수는 그 카드 · 그 달 · 미루기만 센다 (다른 달 · 실패 · 다른 카드 · 남의 것은 빠짐)")
  void countInMonth() {
    postponed(1L, 7L, LocalDate.of(2026, 10, 5));
    postponed(1L, 7L, LocalDate.of(2026, 10, 31));
    postponed(1L, 7L, LocalDate.of(2026, 9, 30));
    postponed(1L, 8L, LocalDate.of(2026, 10, 6));
    postponed(2L, 7L, LocalDate.of(2026, 10, 7));
    em.persistAndFlush(
        CardDayMark.failed(1L, 7L, LocalDate.of(2026, 10, 12), MissReason.TIRED, null, false, NOW));

    assertThat(
            repository.countByUserIdAndCardIdAndMarkAndMarkDateBetween(
                1L,
                7L,
                CardDayMarkType.POSTPONED,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)))
        .isEqualTo(2);
    assertThat(repository.countByUserIdAndCardIdAndMark(1L, 7L, CardDayMarkType.POSTPONED))
        .isEqualTo(3);
    assertThat(repository.existsByUserIdAndCardIdAndMarkDate(1L, 7L, LocalDate.of(2026, 10, 12)))
        .isTrue();
    assertThat(repository.existsByUserIdAndCardIdAndMarkDate(2L, 7L, LocalDate.of(2026, 10, 12)))
        .isFalse();
  }

  @Test
  @DisplayName("기간 목록은 내 표시만 날짜 순으로 나온다")
  void findInPeriod() {
    postponed(1L, 7L, LocalDate.of(2026, 10, 12));
    postponed(1L, 8L, LocalDate.of(2026, 10, 5));
    postponed(2L, 7L, LocalDate.of(2026, 10, 6));
    postponed(1L, 7L, LocalDate.of(2026, 11, 2));
    em.clear();

    assertThat(
            repository.findByUserIdAndMarkDateBetweenOrderByMarkDateAscIdAsc(
                1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        .extracting(CardDayMark::getMarkDate)
        .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12));
  }

  @Test
  @DisplayName("같은 카드 · 같은 날짜에 표시를 두 번 넣으면 DB 가 막는다")
  void oneMarkPerCardDay() {
    postponed(1L, 7L, LocalDate.of(2026, 10, 12));

    assertThatThrownBy(
            () ->
                em.persistAndFlush(
                    CardDayMark.failed(
                        1L, 7L, LocalDate.of(2026, 10, 12), MissReason.ETC, null, false, NOW)))
        .isInstanceOf(Exception.class);
  }
}
