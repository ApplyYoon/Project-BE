package com.ithopeschool.projectbe.domain.safety;

import java.time.Instant;
import java.time.ZoneId;

// "연락 금지 시간" 검사기입니다. (이슈 #57, 기획서 v2 5장 「안전 브레이크」)
// 사용자 대부분이 미성년자라서, 밤늦게는 메시지·푸시·전화를 보내지 않습니다.
// 무엇이든 보내기 직전에 이 검사를 먼저 거칩니다.
//
//   밤 11시 30분, 금지 23시~7시  →  true  (보내면 안 됨)
//   아침 8시,     금지 23시~7시  →  false (보내도 됨)
//
// FE 의 isWithinQuietHours (src/lib/safetyBrake.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 두 화면의 판단이 어긋나지 않습니다.
public final class QuietHours {

  // 배포 서버는 보통 UTC 로 돌아가서, 서버 컴퓨터의 시간대에 기대지 않고 한국 시간을 직접 지정합니다.
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 기본 연락 금지 시간: 밤 11시 ~ 아침 7시 (기획서 기본값)
  public static final int DEFAULT_START_HOUR = 23;
  public static final int DEFAULT_END_HOUR = 7;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private QuietHours() {}

  // 기본 금지 시간(23시~7시)으로 검사합니다.
  public static boolean isQuiet(Instant now) {
    return isQuiet(now, DEFAULT_START_HOUR, DEFAULT_END_HOUR);
  }

  // 지금(now)이 연락 금지 시간이면 true 를 돌려줍니다.
  // startHour 시 정각부터 endHour 시 정각 직전까지가 금지 시간입니다. (둘 다 0~23)
  public static boolean isQuiet(Instant now, int startHour, int endHour) {
    // 시각이 0~23 을 벗어나면 조용히 틀린 답을 내는 대신 바로 알립니다.
    // (예: 30 이 들어오면 "금지 시간이 아님" 으로 계산돼서 한밤중에 연락이 나갑니다)
    if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
      throw new IllegalArgumentException("연락 금지 시각은 0~23 사이여야 합니다");
    }

    // 한국 시간으로 지금이 몇 시인지 꺼냅니다. (23시 30분이면 23)
    int hour = now.atZone(SEOUL).getHour();

    if (startHour < endHour) {
      // 자정을 넘지 않는 범위 (예: 9시 ~ 18시)
      return hour >= startHour && hour < endHour;
    }
    if (startHour == endHour) {
      // 시작과 끝이 같으면 금지 시간이 없는 것으로 봅니다. (FE 와 같은 규칙)
      return false;
    }
    // 자정을 넘는 범위 (예: 23시 ~ 7시) — "23시 이후" 이거나 "7시 이전" 이면 금지 시간입니다.
    return hour >= startHour || hour < endHour;
  }
}
