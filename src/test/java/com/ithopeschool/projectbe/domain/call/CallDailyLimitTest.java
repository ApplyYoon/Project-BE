package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. 시계를 원하는 시각으로 돌려 가며 하루 통화 한도를 봅니다.
class CallDailyLimitTest {

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
  @DisplayName("한도까지는 걸 수 있고, 넘으면 걸 수 없다. 사람마다 따로 센다")
  void allowsUpToTheLimitPerUser() {
    CallDailyLimit limit = new CallDailyLimit(2, clock);

    assertThat(limit.tryUse(7L)).isTrue();
    assertThat(limit.tryUse(7L)).isTrue();
    assertThat(limit.tryUse(7L)).isFalse();
    assertThat(limit.tryUse(8L)).isTrue();
  }

  @Test
  @DisplayName("날이 바뀌면(새벽 4시, 한국 시간) 다시 걸 수 있다")
  void resetsAtFourAmKoreaTime() {
    CallDailyLimit limit = new CallDailyLimit(1, clock);
    assertThat(limit.tryUse(7L)).isTrue();

    // 10월 12일 03:59 (한국 시간) = 아직 10월 11일로 칩니다.
    clock.moveTo("2026-10-11T18:59:00Z");
    assertThat(limit.tryUse(7L)).isFalse();

    // 10월 12일 04:00 (한국 시간) = 날이 바뀌었습니다.
    clock.moveTo("2026-10-11T19:00:00Z");
    assertThat(limit.tryUse(7L)).isTrue();
  }

  @Test
  @DisplayName("통화가 시작되지 않았으면 횟수를 되돌릴 수 있다 (0 아래로는 내려가지 않는다)")
  void giveBackReturnsOneUse() {
    CallDailyLimit limit = new CallDailyLimit(1, clock);
    assertThat(limit.tryUse(7L)).isTrue();
    assertThat(limit.tryUse(7L)).isFalse();

    limit.giveBack(7L);
    assertThat(limit.tryUse(7L)).isTrue();

    // 쓴 적이 없는 사람의 것을 되돌려도 한도가 늘어나지 않습니다.
    limit.giveBack(9L);
    limit.giveBack(9L);
    assertThat(limit.tryUse(9L)).isTrue();
    assertThat(limit.tryUse(9L)).isFalse();
  }

  @Test
  @DisplayName("한도가 0 이면 아무도 걸 수 없다 (통화를 잠깐 막고 싶을 때)")
  void zeroLimitBlocksEveryone() {
    assertThat(new CallDailyLimit(0, clock).tryUse(7L)).isFalse();
  }
}
