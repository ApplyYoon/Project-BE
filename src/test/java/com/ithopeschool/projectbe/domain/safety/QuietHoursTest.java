package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

// "연락 금지 시간" 규칙(이슈 #57)을 확인합니다.
// 앞의 3개는 FE 함수(isWithinQuietHours) 주석의 예시를 그대로 옮긴 것입니다.
class QuietHoursTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 한국 시간 "시:분:초" 를 순간(Instant)으로 바꾸는 도우미입니다. 날짜는 2026년 9월 28일로 고정합니다.
  private static Instant seoul(int hour, int minute, int second) {
    return ZonedDateTime.of(2026, 9, 28, hour, minute, second, 0, SEOUL).toInstant();
  }

  @Test
  void feExample1LateNightIsQuiet() {
    // FE 예1) 지금 23시 30분, 23시~7시 → true (자정을 넘는 범위 안)
    assertThat(QuietHours.isQuiet(seoul(23, 30, 0), 23, 7)).isTrue();
  }

  @Test
  void feExample2MorningIsNotQuiet() {
    // FE 예2) 지금 8시, 23시~7시 → false (범위 밖)
    assertThat(QuietHours.isQuiet(seoul(8, 0, 0), 23, 7)).isFalse();
  }

  @Test
  void feExample3RangeThatDoesNotCrossMidnight() {
    // FE 예3) 지금 10시, 9시~18시 → true (자정을 안 넘는 범위)
    assertThat(QuietHours.isQuiet(seoul(10, 0, 0), 9, 18)).isTrue();
  }

  @Test
  void startsExactlyAtStartHour() {
    // 22:59:59 는 아직 아님, 23:00:00 부터 금지
    assertThat(QuietHours.isQuiet(seoul(22, 59, 59), 23, 7)).isFalse();
    assertThat(QuietHours.isQuiet(seoul(23, 0, 0), 23, 7)).isTrue();
  }

  @Test
  void endsExactlyAtEndHour() {
    // 06:59:59 까지 금지, 07:00:00 부터는 보내도 됨
    assertThat(QuietHours.isQuiet(seoul(6, 59, 59), 23, 7)).isTrue();
    assertThat(QuietHours.isQuiet(seoul(7, 0, 0), 23, 7)).isFalse();
  }

  @Test
  void afterMidnightIsStillQuiet() {
    // 자정을 넘어도 금지 시간이 이어집니다.
    assertThat(QuietHours.isQuiet(seoul(0, 0, 0), 23, 7)).isTrue();
    assertThat(QuietHours.isQuiet(seoul(3, 0, 0), 23, 7)).isTrue();
  }

  @Test
  void rangeThatDoesNotCrossMidnightHasBothEdges() {
    // 9시~18시: 9시 정각부터, 18시 정각 직전까지
    assertThat(QuietHours.isQuiet(seoul(8, 59, 59), 9, 18)).isFalse();
    assertThat(QuietHours.isQuiet(seoul(9, 0, 0), 9, 18)).isTrue();
    assertThat(QuietHours.isQuiet(seoul(17, 59, 59), 9, 18)).isTrue();
    assertThat(QuietHours.isQuiet(seoul(18, 0, 0), 9, 18)).isFalse();
  }

  @Test
  void sameStartAndEndMeansNoQuietHours() {
    // 시작과 끝이 같으면 금지 시간이 없습니다. (FE 와 같은 규칙)
    assertThat(QuietHours.isQuiet(seoul(23, 0, 0), 23, 23)).isFalse();
    assertThat(QuietHours.isQuiet(seoul(12, 0, 0), 23, 23)).isFalse();
  }

  @Test
  void defaultIsElevenPmToSevenAm() {
    // 시각을 안 넘기면 기본값(23시~7시)으로 검사합니다.
    assertThat(QuietHours.isQuiet(seoul(23, 30, 0))).isTrue();
    assertThat(QuietHours.isQuiet(seoul(6, 59, 59))).isTrue();
    assertThat(QuietHours.isQuiet(seoul(7, 0, 0))).isFalse();
    assertThat(QuietHours.isQuiet(seoul(12, 0, 0))).isFalse();
  }

  @Test
  void worksWithUtcInstantsLikeADeployedServer() {
    // 배포 서버는 UTC 로 돌아갑니다. UTC 14:30 = 한국 시간 23:30 → 금지 시간
    assertThat(QuietHours.isQuiet(Instant.parse("2026-09-28T14:30:00Z"))).isTrue();
    // UTC 23:30 = 한국 시간 다음 날 08:30 → 금지 시간이 아님 (UTC 시각 23 을 그대로 보면 틀립니다)
    assertThat(QuietHours.isQuiet(Instant.parse("2026-09-28T23:30:00Z"))).isFalse();
  }

  @Test
  void doesNotDependOnTheServersTimeZone() {
    // 서버 컴퓨터의 시간대를 하와이로 바꿔도 결과가 같아야 합니다. (시간대를 직접 지정했는지 확인)
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
      assertThat(QuietHours.isQuiet(Instant.parse("2026-09-28T14:30:00Z"))).isTrue();
      assertThat(QuietHours.isQuiet(Instant.parse("2026-09-28T23:30:00Z"))).isFalse();
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  void rejectsHoursOutsideZeroToTwentyThree() {
    // 0~23 을 벗어난 시각은 계산하지 않고 바로 알립니다.
    assertThatThrownBy(() -> QuietHours.isQuiet(seoul(12, 0, 0), 24, 7))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> QuietHours.isQuiet(seoul(12, 0, 0), 23, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
