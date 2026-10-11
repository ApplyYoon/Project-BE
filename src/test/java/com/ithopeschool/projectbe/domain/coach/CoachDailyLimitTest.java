package com.ithopeschool.projectbe.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. 시계를 원하는 시각으로 돌려 가며 하루 한도를 봅니다.
class CoachDailyLimitTest {

  // 테스트에서 시각을 바꿀 수 있는 시계입니다.
  private static class MovableClock extends Clock {
    private Instant now;

    MovableClock(Instant start) {
      this.now = start;
    }

    void moveTo(String instant) {
      this.now = Instant.parse(instant);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  // 2026-10-11 15:00 (한국 시간) = 06:00 UTC
  private final MovableClock clock = new MovableClock(Instant.parse("2026-10-11T06:00:00Z"));

  @Test
  @DisplayName("한도까지는 쓸 수 있고, 한도를 넘으면 쓸 수 없다")
  void allowsUpToTheLimit() {
    CoachDailyLimit limit = new CoachDailyLimit(3, clock);

    assertThat(limit.tryUse(7L)).isTrue();
    assertThat(limit.tryUse(7L)).isTrue();
    assertThat(limit.tryUse(7L)).isTrue();
    assertThat(limit.tryUse(7L)).isFalse();
    assertThat(limit.tryUse(7L)).isFalse();
  }

  @Test
  @DisplayName("사람마다 따로 센다 (한 사람이 다 써도 다른 사람은 쓸 수 있다)")
  void countsPerUser() {
    CoachDailyLimit limit = new CoachDailyLimit(1, clock);

    assertThat(limit.tryUse(7L)).isTrue();
    assertThat(limit.tryUse(7L)).isFalse();
    assertThat(limit.tryUse(8L)).isTrue();
  }

  @Test
  @DisplayName("날이 바뀌면(새벽 4시, 한국 시간) 다시 쓸 수 있다")
  void resetsAtFourAmKoreaTime() {
    CoachDailyLimit limit = new CoachDailyLimit(1, clock);
    assertThat(limit.tryUse(7L)).isTrue();
    assertThat(limit.tryUse(7L)).isFalse();

    // 10월 12일 03:59 (한국 시간) = 아직 10월 11일로 칩니다.
    clock.moveTo("2026-10-11T18:59:00Z");
    assertThat(limit.tryUse(7L)).isFalse();

    // 10월 12일 04:00 (한국 시간) = 날이 바뀌었습니다.
    clock.moveTo("2026-10-11T19:00:00Z");
    assertThat(limit.tryUse(7L)).isTrue();
    assertThat(limit.tryUse(7L)).isFalse();
  }

  @Test
  @DisplayName("한도가 0 이면 아무도 쓸 수 없다 (AI 를 잠깐 막고 싶을 때)")
  void zeroLimitBlocksEveryone() {
    CoachDailyLimit limit = new CoachDailyLimit(0, clock);

    assertThat(limit.tryUse(7L)).isFalse();
  }
}
