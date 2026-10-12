package com.ithopeschool.projectbe.domain.call;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

// "지금 AI 전화를 울려야 하는지" 를 정합니다. (이슈 #32 의 통화 시간 알림)
// 학생이 정해 둔 전화 시간이 되면 하루에 한 번 알립니다. 예약 작업(#9)이 몇 분마다 이 함수를 부르면 됩니다.
//
// 아래가 모두 맞을 때만 울립니다.
//   1. 전화 받을 시간을 정해 두었다
//   2. 지금이 그 시간이거나, 그 시간이 지난 지 5분이 안 됐다
//      (예약 작업이 딱 그 1분에 돌지 않아도 놓치지 않게 여유를 둡니다)
//   3. 최근 12시간 안에 이미 울린 적이 없다  (하루에 한 번만. 자정을 넘기는 시간도 안전합니다)
//
// 예시
//   시간 22:00, 지금 21:59, 울린 적 없음            →  false  (아직 안 됨)
//   시간 22:00, 지금 22:00, 울린 적 없음            →  true   (딱 그 시간)
//   시간 22:00, 지금 22:04, 울린 적 없음            →  true   (5분 안이라 아직 울림)
//   시간 22:00, 지금 22:05, 울린 적 없음            →  false  (5분이 지나서 놓침)
//   시간 22:00, 지금 22:00, 방금 울렸음              →  false  (같은 시간에 두 번 울리지 않음)
//   시간 23:58, 지금 다음 날 00:01, 어제 울렸음      →  false  (자정을 넘겨도 12시간 안이라 안 울림)
//   시간 23:58, 지금 00:01, 처음                    →  true   (자정을 넘겨도 5분 안이면 울림)
//   시간을 안 정했다                                 →  false
//
// 시각은 한국 시간의 시계 그대로 봅니다. "하루의 기준 새벽 4시" 와는 상관없습니다. (22:00 은 그냥 밤 10시입니다)
//
// FE 의 shouldRingNow (src/lib/callReminder.ts) 와 같은 규칙입니다.
// FE 는 앱이 열려 있을 때만 울릴 수 있어서, 앱이 꺼져 있을 때는 서버가 같은 규칙으로 알려야 합니다.
public final class CallRing {

  // 정해진 시간으로부터 몇 분 안이면 울릴지입니다.
  public static final int RING_WINDOW_MINUTES = 5;

  // 한 번 울린 뒤 다시 울리지 않는 시간입니다. 하루에 한 번만 울리게 합니다.
  public static final Duration RING_COOLDOWN = Duration.ofHours(12);

  private static final int MINUTES_PER_DAY = 24 * 60;

  // 배포 서버는 보통 UTC 로 돌아가서, 서버 컴퓨터의 시간대에 기대지 않고 한국 시간을 직접 지정합니다.
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private CallRing() {}

  // 지금 전화를 울려야 하면 true 입니다.
  //
  // now: 지금 시각
  // callTime: 전화 받을 시간. 아직 안 정했으면 null
  // lastRingAt: 마지막으로 전화를 울린 시각. 한 번도 울린 적이 없으면 null
  public static boolean shouldRingNow(Instant now, LocalTime callTime, Instant lastRingAt) {
    if (callTime == null) {
      return false;
    }

    LocalTime nowTime = now.atZone(SEOUL).toLocalTime();
    int nowMinutes = nowTime.getHour() * 60 + nowTime.getMinute();
    int scheduledMinutes = callTime.getHour() * 60 + callTime.getMinute();
    // 정해진 시간으로부터 몇 분 지났는지. 자정을 넘겨도(23:58 → 00:01 = 3분) 맞게 나오도록 하루(1440분)를 더해서 나눕니다.
    int minutesPassed = (nowMinutes - scheduledMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY;
    if (minutesPassed >= RING_WINDOW_MINUTES) {
      return false;
    }

    if (lastRingAt != null) {
      Duration sinceLastRing = Duration.between(lastRingAt, now);
      // 음수는 시계를 뒤로 돌린 경우라서 무시합니다.
      if (!sinceLastRing.isNegative() && sinceLastRing.compareTo(RING_COOLDOWN) < 0) {
        return false;
      }
    }
    return true;
  }
}
