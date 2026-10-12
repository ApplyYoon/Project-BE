package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. 지금 AI 전화를 울려야 하는지 정하는 규칙(이슈 #32)을 봅니다.
// "FE 예" 로 시작하는 8개는 FE 함수(shouldRingNow) 주석의 예시를 그대로 옮긴 것입니다.
class CallRingTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalTime TEN_PM = LocalTime.of(22, 0);
  private static final LocalTime BEFORE_MIDNIGHT = LocalTime.of(23, 58);

  // 한국 시간 10월 "day일 시:분:초" 를 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute, int second) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, second, 0, SEOUL).toInstant();
  }

  private static Instant at(int day, int hour, int minute) {
    return at(day, hour, minute, 0);
  }

  @Test
  @DisplayName("FE 예) 시간 22:00, 지금 21:59, 울린 적 없음 → 아직 안 울린다")
  void notYet() {
    assertThat(CallRing.shouldRingNow(at(12, 21, 59), TEN_PM, null)).isFalse();
  }

  @Test
  @DisplayName("FE 예) 시간 22:00, 지금 22:00, 울린 적 없음 → 울린다")
  void exactlyOnTime() {
    assertThat(CallRing.shouldRingNow(at(12, 22, 0), TEN_PM, null)).isTrue();
  }

  @Test
  @DisplayName("FE 예) 시간 22:00, 지금 22:04 → 5분 안이라 아직 울린다")
  void withinTheWindow() {
    assertThat(CallRing.shouldRingNow(at(12, 22, 4), TEN_PM, null)).isTrue();
    // 초는 보지 않습니다. 22:04:59 도 4분 지난 것입니다.
    assertThat(CallRing.shouldRingNow(at(12, 22, 4, 59), TEN_PM, null)).isTrue();
  }

  @Test
  @DisplayName("FE 예) 시간 22:00, 지금 22:05 → 5분이 지나서 울리지 않는다")
  void afterTheWindow() {
    assertThat(CallRing.shouldRingNow(at(12, 22, 5), TEN_PM, null)).isFalse();
    assertThat(CallRing.shouldRingNow(at(12, 23, 30), TEN_PM, null)).isFalse();
  }

  @Test
  @DisplayName("FE 예) 시간 22:00, 지금 22:00, 방금 울렸음 → 같은 시간에 두 번 울리지 않는다")
  void doesNotRingTwice() {
    assertThat(CallRing.shouldRingNow(at(12, 22, 0, 30), TEN_PM, at(12, 22, 0, 5))).isFalse();
    assertThat(CallRing.shouldRingNow(at(12, 22, 3), TEN_PM, at(12, 22, 0))).isFalse();
  }

  @Test
  @DisplayName("FE 예) 시간 23:58, 지금 다음 날 00:01, 어제 울렸음 → 자정을 넘겨도 다시 울리지 않는다")
  void doesNotRingAgainAfterMidnight() {
    assertThat(CallRing.shouldRingNow(at(13, 0, 1), BEFORE_MIDNIGHT, at(12, 23, 58))).isFalse();
  }

  @Test
  @DisplayName("FE 예) 시간 23:58, 지금 00:01, 처음 → 자정을 넘겨도 5분 안이면 울린다")
  void ringsWithinTheWindowAcrossMidnight() {
    assertThat(CallRing.shouldRingNow(at(13, 0, 1), BEFORE_MIDNIGHT, null)).isTrue();
    assertThat(CallRing.shouldRingNow(at(13, 0, 3), BEFORE_MIDNIGHT, null)).isFalse();
  }

  @Test
  @DisplayName("FE 예) 시간을 안 정했으면 울리지 않는다")
  void noCallTime() {
    assertThat(CallRing.shouldRingNow(at(12, 22, 0), null, null)).isFalse();
  }

  @Test
  @DisplayName("어제 같은 시간에 울렸어도 오늘 그 시간이 되면 다시 울린다 (하루에 한 번)")
  void ringsAgainTheNextDay() {
    assertThat(CallRing.shouldRingNow(at(13, 22, 0), TEN_PM, at(12, 22, 0))).isTrue();
    // 어제 22:04 에 울렸고 오늘 22:00 이면 23시간 56분 뒤입니다. 12시간이 지났으니 울립니다.
    assertThat(CallRing.shouldRingNow(at(13, 22, 0), TEN_PM, at(12, 22, 4))).isTrue();
  }

  @Test
  @DisplayName("전화 시간을 바꿨어도 마지막으로 울린 지 12시간이 안 됐으면 울리지 않는다")
  void changedCallTimeStillRespectsTheCooldown() {
    // 오전 9시에 울린 뒤 전화 시간을 저녁 8시(11시간 뒤)로 바꿨습니다.
    assertThat(CallRing.shouldRingNow(at(12, 20, 0), LocalTime.of(20, 0), at(12, 9, 0))).isFalse();
    // 저녁 9시(딱 12시간 뒤)로 바꿨으면 울립니다.
    assertThat(CallRing.shouldRingNow(at(12, 21, 0), LocalTime.of(21, 0), at(12, 9, 0))).isTrue();
  }

  @Test
  @DisplayName("마지막으로 울린 시각이 지금보다 뒤(시계를 뒤로 돌림)면 무시하고 울린다")
  void lastRingInTheFutureIsIgnored() {
    assertThat(CallRing.shouldRingNow(at(12, 22, 0), TEN_PM, at(12, 23, 0))).isTrue();
  }

  @Test
  @DisplayName("자정(00:00)으로 정해도 맞게 울린다")
  void midnightCallTime() {
    LocalTime midnight = LocalTime.of(0, 0);

    assertThat(CallRing.shouldRingNow(at(12, 23, 59), midnight, null)).isFalse();
    assertThat(CallRing.shouldRingNow(at(13, 0, 0), midnight, null)).isTrue();
    assertThat(CallRing.shouldRingNow(at(13, 0, 4), midnight, null)).isTrue();
    assertThat(CallRing.shouldRingNow(at(13, 0, 5), midnight, null)).isFalse();
  }
}
