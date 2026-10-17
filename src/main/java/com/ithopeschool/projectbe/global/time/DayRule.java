package com.ithopeschool.projectbe.global.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

// "하루의 기준" 계산기입니다. (docs/API.md 2-4)
// 밤늦게 공부하는 학생이 많아서, 새벽 4시(한국 시간)에 날짜가 바뀝니다.
// 00:00 ~ 03:59 에 한 일은 전날로 칩니다.
//
//   9월 28일 23:30  →  2026-09-28
//   9월 29일 01:30  →  2026-09-28  (전날)
//   9월 29일 04:00  →  2026-09-29
//
// 순공시간·달성률·요약표처럼 "날짜별로 센다" 면 어디서든 이 계산기를 씁니다.
// 한 곳이라도 자정 기준으로 세면 숫자가 서로 안 맞습니다.
public final class DayRule {

  // 배포 서버는 보통 UTC 로 돌아가서, 서버 컴퓨터의 시간대에 기대지 않고 한국 시간을 직접 지정합니다.
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 하루가 시작되는 시각(한국 시간 새벽 4시)
  private static final int DAY_START_HOUR = 4;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private DayRule() {}

  // 어떤 순간(instant)이 "몇 월 며칠" 에 속하는지 돌려줍니다.
  public static LocalDate dateOf(Instant instant) {
    // 1) 한국 시간으로 바꾸고  2) 4시간을 빼고  3) 날짜 부분만 꺼냅니다.
    return instant.atZone(SEOUL).minusHours(DAY_START_HOUR).toLocalDate();
  }

  // 그날이 시작되는 순간(한국 시간 새벽 4시)을 돌려줍니다. "오늘 이미 보냈는지" 처럼 그날 안의 것을 찾을 때 씁니다.
  //   2026-09-28  →  2026-09-28 04:00 (한국 시간) = 2026-09-27T19:00:00Z
  public static Instant startOf(LocalDate date) {
    return date.atTime(DAY_START_HOUR, 0).atZone(SEOUL).toInstant();
  }
}
