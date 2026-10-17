package com.ithopeschool.projectbe.domain.dailysignal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// 하루 신호를 실제 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class DailySignalRepositoryTest {

  @Autowired private DailySignalRepository dailySignalRepository;

  private DailySignal save(Long userId, String date, Integer steps) {
    return dailySignalRepository.saveAndFlush(
        new DailySignal(userId, LocalDate.parse(date), null, null, null, steps, null));
  }

  @Test
  @DisplayName("모든 칸이 다 차 있어도, 다 비어 있어도 저장되고 그대로 꺼내진다")
  void savesFullAndEmptyDays() {
    Instant sleptAt = Instant.parse("2026-10-16T14:30:00Z");
    Instant wokeAt = Instant.parse("2026-10-16T22:00:00Z");
    dailySignalRepository.saveAndFlush(
        new DailySignal(1L, LocalDate.parse("2026-10-17"), 420, sleptAt, wokeAt, 8500, true));
    save(1L, "2026-10-16", null);

    DailySignal full =
        dailySignalRepository
            .findByUserIdAndSignalDate(1L, LocalDate.parse("2026-10-17"))
            .orElseThrow();
    assertThat(full.getSleepMinutes()).isEqualTo(420);
    assertThat(full.getSleptAt()).isEqualTo(sleptAt);
    assertThat(full.getWokeAt()).isEqualTo(wokeAt);
    assertThat(full.getSteps()).isEqualTo(8500);
    assertThat(full.getSnsOverThirtyMinutes()).isTrue();

    DailySignal empty =
        dailySignalRepository
            .findByUserIdAndSignalDate(1L, LocalDate.parse("2026-10-16"))
            .orElseThrow();
    assertThat(empty.getSteps()).isNull();
    assertThat(empty.getSnsOverThirtyMinutes()).isNull();
  }

  @Test
  @DisplayName("한 사람이 같은 날짜로 두 줄을 만들 수는 없다 (다른 사람이면 괜찮다)")
  void oneRowPerUserPerDay() {
    save(1L, "2026-10-17", 100);
    save(2L, "2026-10-17", 200);

    assertThatThrownBy(() -> save(1L, "2026-10-17", 300))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("기간으로 찾으면 데이터가 있는 날만, 내 것만, 날짜 순서로 나온다")
  void findsOnlyDaysWithData() {
    save(1L, "2026-10-15", 300);
    save(1L, "2026-10-13", 100);
    save(1L, "2026-10-20", 900);
    save(2L, "2026-10-14", 200);

    List<DailySignal> found =
        dailySignalRepository.findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(
            1L, LocalDate.parse("2026-10-13"), LocalDate.parse("2026-10-16"));

    // 10/14·10/16 은 데이터가 없어서 목록에 없습니다. 10/20 은 기간 밖, 2번 사용자 것은 남의 것입니다.
    assertThat(found)
        .extracting(DailySignal::getSignalDate)
        .containsExactly(LocalDate.parse("2026-10-13"), LocalDate.parse("2026-10-15"));
  }
}
