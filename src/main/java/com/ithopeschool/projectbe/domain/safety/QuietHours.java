package com.ithopeschool.projectbe.domain.safety;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

// "연락 금지 시간" 검사기입니다. (이슈 #57, 기획서 v2 5장 「안전 브레이크」)
// 사용자 대부분이 미성년자라서, 밤늦게는 메시지·푸시·전화를 보내지 않습니다.
// 무엇이든 보내기 직전에 이 검사를 먼저 거칩니다.
//
//   밤 11시 30분, 금지 23:00~07:00  →  true  (보내면 안 됨)
//   아침 8시,     금지 23:00~07:00  →  false (보내도 됨)
//   밤 10시 40분, 금지 22:30~07:30  →  true  (분까지 봅니다)
//
// FE 의 isWithinQuietHours (src/lib/safetyBrake.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 두 화면의 판단이 어긋나지 않습니다.
public final class QuietHours {

  // 배포 서버는 보통 UTC 로 돌아가서, 서버 컴퓨터의 시간대에 기대지 않고 한국 시간을 직접 지정합니다.
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 기본 연락 금지 시간: 밤 11시 ~ 아침 7시 (기획서 기본값)
  public static final LocalTime DEFAULT_START = LocalTime.of(23, 0);
  public static final LocalTime DEFAULT_END = LocalTime.of(7, 0);

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private QuietHours() {}

  // 기본 금지 시간(23:00~07:00)으로 검사합니다.
  public static boolean isQuiet(Instant now) {
    return isQuiet(now, DEFAULT_START, DEFAULT_END);
  }

  // 지금(now)이 연락 금지 시간이면 true 를 돌려줍니다.
  // start 부터 end 직전까지가 금지 시간입니다. 분까지 보고, 초는 보지 않습니다.
  // (LocalTime 은 "22:30" 같은 시각 하나입니다. LocalTime.of(22, 30) 또는 LocalTime.parse("22:30") 으로 만듭니다)
  public static boolean isQuiet(Instant now, LocalTime start, LocalTime end) {
    // 시와 분을 "자정부터 몇 분째인지" 하나의 숫자로 바꿔서 비교합니다. (23:30 → 1410)
    int nowMinutes = minutesOfDay(now.atZone(SEOUL).toLocalTime());
    int startMinutes = minutesOfDay(start);
    int endMinutes = minutesOfDay(end);

    if (startMinutes < endMinutes) {
      // 자정을 넘지 않는 범위 (예: 9시 ~ 18시)
      return nowMinutes >= startMinutes && nowMinutes < endMinutes;
    }
    if (startMinutes == endMinutes) {
      // 시작과 끝이 같으면 금지 시간이 없는 것으로 봅니다. (FE 와 같은 규칙)
      return false;
    }
    // 자정을 넘는 범위 (예: 23시 ~ 7시) — "23시 이후" 이거나 "7시 이전" 이면 금지 시간입니다.
    return nowMinutes >= startMinutes || nowMinutes < endMinutes;
  }

  // 사용자가 정한 연락 금지 시간 (시작 · 끝). 관리 설정(#22)에서 읽어 옵니다. (settings/QuietHoursSettings)
  public record Window(LocalTime start, LocalTime end) {

    // 기본 연락 금지 시간 (23:00 ~ 07:00)
    public static final Window DEFAULT = new Window(DEFAULT_START, DEFAULT_END);
  }

  // 보낼지 판단한 결과입니다.
  //   SEND   지금 보냅니다                     (sendAt = 지금)
  //   DELAY  금지 시간이 끝나면 보냅니다         (sendAt = 금지 시간이 끝나는 순간)
  //   DROP   보내지 않습니다                   (sendAt = null)
  public enum Action {
    SEND,
    DELAY,
    DROP
  }

  public record Decision(Action action, Instant sendAt) {}

  // 지금(now) 이 메시지를 보낼지 정합니다. 메시지·푸시·전화를 보내기 직전에 이것을 부릅니다. (이슈 #57)
  //
  //   금지 시간이 아님                        →  SEND  (지금)
  //   금지 시간 + 미룰 메시지(DELAY)          →  DELAY (금지 시간이 끝나는 순간, 한국 시간)
  //   금지 시간 + 버릴 메시지(DROP)           →  DROP
  //
  //   예) 밤 11시 30분, 23:00~07:00, 목표 달성 칭찬(DELAY)  →  DELAY, 다음 날 아침 7시
  //   예) 새벽 1시,     23:00~07:00, 목표 달성 칭찬(DELAY)  →  DELAY, 같은 날 아침 7시
  //   예) 밤 11시 30분, 23:00~07:00, 계획 시간 지남(DROP)   →  DROP
  public static Decision decide(Instant now, Window window, QuietPolicy policy) {
    if (!isQuiet(now, window.start(), window.end())) {
      return new Decision(Action.SEND, now);
    }
    if (policy == QuietPolicy.DROP) {
      return new Decision(Action.DROP, null);
    }
    return new Decision(Action.DELAY, nextEnd(now, window.end()));
  }

  // 지금 다음으로 오는 "금지 시간이 끝나는 순간" (한국 시간)
  //   지금 10/17 23:30, 끝 07:00  →  10/18 07:00
  //   지금 10/18 01:00, 끝 07:00  →  10/18 07:00
  private static Instant nextEnd(Instant now, LocalTime end) {
    ZonedDateTime nowInSeoul = now.atZone(SEOUL);
    // 분까지만 봅니다. (초는 버림, isQuiet 과 같은 규칙)
    ZonedDateTime todayEnd =
        nowInSeoul.toLocalDate().atTime(end.getHour(), end.getMinute()).atZone(SEOUL);
    return todayEnd.isAfter(nowInSeoul) ? todayEnd.toInstant() : todayEnd.plusDays(1).toInstant();
  }

  // 시각을 "자정부터 몇 분째인지" 로 바꿉니다. (07:30 → 450)
  private static int minutesOfDay(LocalTime time) {
    return time.getHour() * 60 + time.getMinute();
  }
}
