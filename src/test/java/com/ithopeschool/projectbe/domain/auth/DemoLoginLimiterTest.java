package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시연 코드를 너무 많이 틀렸을 때 막는 규칙(DemoLoginLimiter)을 확인합니다. 서버를 켜지 않습니다.
class DemoLoginLimiterTest {

  private static final Instant NOW = Instant.parse("2026-10-18T09:00:00Z");

  // 지금 시각을 바꿀 수 있는 시계 (1분이 지난 뒤를 흉내 냅니다)
  private static final class MovableClock extends Clock {
    private Instant now = NOW;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MovableClock clock = new MovableClock();
  private final DemoLoginLimiter limiter = new DemoLoginLimiter(clock);

  private void failTimes(String place, int times) {
    for (int i = 0; i < times; i++) {
      limiter.recordFail(place);
    }
  }

  @Test
  @DisplayName("같은 곳에서 1분 안에 4번 틀리면 아직 괜찮고, 5번째부터 막힌다")
  void blocksAfterFiveFailsFromOnePlace() {
    failTimes("1.1.1.1", 4);
    assertThat(limiter.isBlocked("1.1.1.1")).isFalse();

    failTimes("1.1.1.1", 1);
    assertThat(limiter.isBlocked("1.1.1.1")).isTrue();
    // 다른 곳은 막히지 않습니다.
    assertThat(limiter.isBlocked("2.2.2.2")).isFalse();
  }

  @Test
  @DisplayName("1분이 지나면 틀린 기록이 사라져서 다시 할 수 있다")
  void unblocksAfterOneMinute() {
    failTimes("1.1.1.1", 5);
    assertThat(limiter.isBlocked("1.1.1.1")).isTrue();

    clock.now = NOW.plus(DemoLoginLimiter.WINDOW).plus(Duration.ofSeconds(1));
    assertThat(limiter.isBlocked("1.1.1.1")).isFalse();
  }

  @Test
  @DisplayName("여러 곳에서 합쳐 30번 틀리면 처음 오는 곳도 막힌다 (IP 를 바꿔 가며 시도하는 경우)")
  void blocksEveryoneAfterThirtyFailsInTotal() {
    for (int i = 0; i < DemoLoginLimiter.MAX_FAILS_TOTAL; i++) {
      limiter.recordFail("10.0.0." + i);
    }
    assertThat(limiter.isBlocked("9.9.9.9")).isTrue();
  }
}
