package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

// "연락 금지 시간" 규칙(이슈 #57)을 확인합니다.
// feExample 로 시작하는 6개는 FE 함수(isWithinQuietHours) 주석의 예시를 그대로 옮긴 것입니다.
class QuietHoursTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 자주 쓰는 금지 시간: 밤 11시 ~ 아침 7시
  private static final LocalTime ELEVEN_PM = LocalTime.of(23, 0);
  private static final LocalTime SEVEN_AM = LocalTime.of(7, 0);

  // 한국 시간 "시:분:초" 를 순간(Instant)으로 바꾸는 도우미입니다. 날짜는 2026년 9월 28일로 고정합니다.
  private static Instant seoul(int hour, int minute, int second) {
    return ZonedDateTime.of(2026, 9, 28, hour, minute, second, 0, SEOUL).toInstant();
  }

  @Test
  void feExample1LateNightIsQuiet() {
    // FE 예1) 지금 23시 30분, 23시~7시 → true (자정을 넘는 범위 안)
    assertThat(QuietHours.isQuiet(seoul(23, 30, 0), ELEVEN_PM, SEVEN_AM)).isTrue();
  }

  @Test
  void feExample2MorningIsNotQuiet() {
    // FE 예2) 지금 8시, 23시~7시 → false (범위 밖)
    assertThat(QuietHours.isQuiet(seoul(8, 0, 0), ELEVEN_PM, SEVEN_AM)).isFalse();
  }

  @Test
  void feExample3RangeThatDoesNotCrossMidnight() {
    // FE 예3) 지금 10시, 9시~18시 → true (자정을 안 넘는 범위)
    assertThat(QuietHours.isQuiet(seoul(10, 0, 0), LocalTime.of(9, 0), LocalTime.of(18, 0)))
        .isTrue();
  }

  @Test
  void feExample4LooksAtMinutes() {
    // FE 예4) 지금 22시 40분, 22:30~07:30 → true (분까지 봅니다)
    LocalTime start = LocalTime.of(22, 30);
    LocalTime end = LocalTime.of(7, 30);
    assertThat(QuietHours.isQuiet(seoul(22, 40, 0), start, end)).isTrue();
    // 22:10 은 아직 금지 시간이 아닙니다. ("시" 만 보면 22시라서 금지로 잘못 나옵니다)
    assertThat(QuietHours.isQuiet(seoul(22, 10, 0), start, end)).isFalse();
    // 끝도 분까지 봅니다. 07:29 까지 금지, 07:30 부터 보내도 됩니다.
    assertThat(QuietHours.isQuiet(seoul(7, 29, 59), start, end)).isTrue();
    assertThat(QuietHours.isQuiet(seoul(7, 30, 0), start, end)).isFalse();
  }

  @Test
  void feExample5RangeInsideOneHour() {
    // FE 예5) 지금 23시 20분, 23:00~23:45 → true (같은 "시" 안의 범위도 됩니다)
    LocalTime start = LocalTime.of(23, 0);
    LocalTime end = LocalTime.of(23, 45);
    assertThat(QuietHours.isQuiet(seoul(23, 20, 0), start, end)).isTrue();
    assertThat(QuietHours.isQuiet(seoul(23, 45, 0), start, end)).isFalse();
  }

  @Test
  void feExample6SameStartAndEndMeansNoQuietHours() {
    // FE 예6) 시작과 끝이 같은 시각 → false (금지 시간이 없는 것으로 봅니다)
    assertThat(QuietHours.isQuiet(seoul(23, 0, 0), ELEVEN_PM, ELEVEN_PM)).isFalse();
    assertThat(QuietHours.isQuiet(seoul(12, 0, 0), ELEVEN_PM, ELEVEN_PM)).isFalse();
  }

  @Test
  void startsExactlyAtStartTime() {
    // 22:59:59 는 아직 아님, 23:00:00 부터 금지
    assertThat(QuietHours.isQuiet(seoul(22, 59, 59), ELEVEN_PM, SEVEN_AM)).isFalse();
    assertThat(QuietHours.isQuiet(seoul(23, 0, 0), ELEVEN_PM, SEVEN_AM)).isTrue();
  }

  @Test
  void endsExactlyAtEndTime() {
    // 06:59:59 까지 금지, 07:00:00 부터는 보내도 됨
    assertThat(QuietHours.isQuiet(seoul(6, 59, 59), ELEVEN_PM, SEVEN_AM)).isTrue();
    assertThat(QuietHours.isQuiet(seoul(7, 0, 0), ELEVEN_PM, SEVEN_AM)).isFalse();
  }

  @Test
  void afterMidnightIsStillQuiet() {
    // 자정을 넘어도 금지 시간이 이어집니다.
    assertThat(QuietHours.isQuiet(seoul(0, 0, 0), ELEVEN_PM, SEVEN_AM)).isTrue();
    assertThat(QuietHours.isQuiet(seoul(3, 0, 0), ELEVEN_PM, SEVEN_AM)).isTrue();
  }

  @Test
  void defaultIsElevenPmToSevenAm() {
    // 시각을 안 넘기면 기본값(23:00~07:00)으로 검사합니다.
    assertThat(QuietHours.isQuiet(seoul(23, 30, 0))).isTrue();
    assertThat(QuietHours.isQuiet(seoul(6, 59, 59))).isTrue();
    assertThat(QuietHours.isQuiet(seoul(7, 0, 0))).isFalse();
    assertThat(QuietHours.isQuiet(seoul(12, 0, 0))).isFalse();
  }

  @Test
  void acceptsTimeTextLikeTheSettings() {
    // FE 설정은 금지 시간을 '22:30' 같은 글자로 저장합니다. LocalTime.parse 로 그대로 읽을 수 있습니다.
    LocalTime start = LocalTime.parse("22:30");
    LocalTime end = LocalTime.parse("07:00");
    assertThat(QuietHours.isQuiet(seoul(22, 30, 0), start, end)).isTrue();
    assertThat(QuietHours.isQuiet(seoul(22, 29, 59), start, end)).isFalse();
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
}
