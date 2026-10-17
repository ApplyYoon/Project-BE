package com.ithopeschool.projectbe.domain.restday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// RestDayRepository 가 진짜 DB(H2)에서 "내 것, 그 달" 만 세고 찾는지, 같은 날을 두 번 넣지 못하는지 확인합니다. (이슈 #225)
@DataJpaTest
class RestDayRepositoryTest {

  @Autowired private RestDayRepository repository;
  @Autowired private TestEntityManager em;

  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

  private void save(Long userId, LocalDate date) {
    em.persistAndFlush(new RestDay(userId, date, NOW));
  }

  @Test
  @DisplayName("그 달의 내 휴식만 세고, 날짜 순으로 찾는다")
  void countAndFind() {
    save(1L, LocalDate.of(2026, 10, 12));
    save(1L, LocalDate.of(2026, 10, 3));
    save(1L, LocalDate.of(2026, 9, 30));
    save(2L, LocalDate.of(2026, 10, 5));
    em.clear();

    LocalDate first = LocalDate.of(2026, 10, 1);
    LocalDate last = LocalDate.of(2026, 10, 31);
    assertThat(repository.countByUserIdAndRestDateBetween(1L, first, last)).isEqualTo(2);
    assertThat(repository.findByUserIdAndRestDateBetweenOrderByRestDate(1L, first, last))
        .extracting(RestDay::getRestDate)
        .containsExactly(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 12));
    assertThat(repository.existsByUserIdAndRestDate(1L, LocalDate.of(2026, 10, 12))).isTrue();
    assertThat(repository.existsByUserIdAndRestDate(2L, LocalDate.of(2026, 10, 12))).isFalse();
  }

  @Test
  @DisplayName("같은 사람의 같은 날을 두 번 넣으면 DB 가 막는다")
  void oneRowPerDay() {
    save(1L, LocalDate.of(2026, 10, 12));

    assertThatThrownBy(() -> save(1L, LocalDate.of(2026, 10, 12))).isInstanceOf(Exception.class);
  }
}
