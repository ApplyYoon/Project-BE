package com.ithopeschool.projectbe.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

// "하루의 기준 = 새벽 4시(한국 시간)" 규칙(docs/API.md 2-4)을 표 그대로 확인합니다.
class DayRuleTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 한국 시간 "년-월-일 시:분:초" 를 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant seoul(int year, int month, int day, int hour, int minute, int second) {
    return ZonedDateTime.of(year, month, day, hour, minute, second, 0, SEOUL).toInstant();
  }

  @Test
  void lateNightBeforeMidnightIsSameDay() {
    // 9월 28일 23:30 → 2026-09-28
    assertThat(DayRule.dateOf(seoul(2026, 9, 28, 23, 30, 0))).isEqualTo(LocalDate.of(2026, 9, 28));
  }

  @Test
  void afterMidnightButBeforeFourIsPreviousDay() {
    // 9월 29일 01:30 → 2026-09-28 (전날)
    assertThat(DayRule.dateOf(seoul(2026, 9, 29, 1, 30, 0))).isEqualTo(LocalDate.of(2026, 9, 28));
    // 0시 정각도 전날
    assertThat(DayRule.dateOf(seoul(2026, 9, 29, 0, 0, 0))).isEqualTo(LocalDate.of(2026, 9, 28));
  }

  @Test
  void oneSecondBeforeFourIsStillPreviousDay() {
    // 9월 29일 03:59:59 → 2026-09-28
    assertThat(DayRule.dateOf(seoul(2026, 9, 29, 3, 59, 59))).isEqualTo(LocalDate.of(2026, 9, 28));
  }

  @Test
  void exactlyFourOClockStartsTheNewDay() {
    // 9월 29일 04:00 → 2026-09-29
    assertThat(DayRule.dateOf(seoul(2026, 9, 29, 4, 0, 0))).isEqualTo(LocalDate.of(2026, 9, 29));
  }

  @Test
  void crossesMonthAndYearBoundaries() {
    // 10월 1일 새벽 3시 → 9월 30일
    assertThat(DayRule.dateOf(seoul(2026, 10, 1, 3, 0, 0))).isEqualTo(LocalDate.of(2026, 9, 30));
    // 1월 1일 새벽 2시 → 작년 12월 31일
    assertThat(DayRule.dateOf(seoul(2027, 1, 1, 2, 0, 0))).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void worksWithUtcInstantsLikeADeployedServer() {
    // 배포 서버는 UTC 로 돌아갑니다. 한국 시간 9월 29일 04:30 = UTC 9월 28일 19:30
    assertThat(DayRule.dateOf(Instant.parse("2026-09-28T19:30:00Z")))
        .isEqualTo(LocalDate.of(2026, 9, 29));
    // 한국 시간 9월 29일 03:59:59 = UTC 9월 28일 18:59:59 → 아직 전날(9월 28일)
    assertThat(DayRule.dateOf(Instant.parse("2026-09-28T18:59:59Z")))
        .isEqualTo(LocalDate.of(2026, 9, 28));
  }

  @Test
  void doesNotDependOnTheServersTimeZone() {
    // 서버 컴퓨터의 시간대를 하와이로 바꿔도 결과가 같아야 합니다. (시간대를 직접 지정했는지 확인)
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
      assertThat(DayRule.dateOf(Instant.parse("2026-09-28T18:59:59Z")))
          .isEqualTo(LocalDate.of(2026, 9, 28));
      assertThat(DayRule.dateOf(Instant.parse("2026-09-28T19:00:00Z")))
          .isEqualTo(LocalDate.of(2026, 9, 29));
    } finally {
      TimeZone.setDefault(original);
    }
  }
}
