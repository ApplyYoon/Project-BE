package com.ithopeschool.projectbe.domain.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

// 시연 코드를 틀린 횟수를 세서, 너무 많이 틀리면 잠깐 막습니다. (DemoLoginService, 2026-10-18)
// 코드를 하나씩 넣어 보며 맞히려는 시도(무차별 대입)를 막으려는 것입니다.
//
//   - 같은 곳(IP)에서 1분 안에 5번 틀리면 → 그곳은 1분 동안 429
//   - 모든 곳을 합쳐 1분 안에 30번 틀리면 → 모두 1분 동안 429 (IP 를 바꿔 가며 시도해도 막히게)
//
// 기록은 서버 메모리에만 있습니다. (새 테이블을 만들지 않습니다) 서버가 다시 켜지면 처음부터 셉니다.
// 여러 요청이 동시에 와도 숫자가 꼬이지 않게 메서드마다 synchronized 를 붙였습니다. (요청이 적어서 느려지지 않습니다)
@Component
public class DemoLoginLimiter {

  // 이 시간 안의 틀린 기록만 셉니다.
  static final Duration WINDOW = Duration.ofMinutes(1);
  // 같은 곳에서 이만큼 틀리면 막습니다.
  static final int MAX_FAILS_PER_PLACE = 5;
  // 모든 곳을 합쳐 이만큼 틀리면 모두 막습니다.
  static final int MAX_FAILS_TOTAL = 30;

  private final Clock clock;
  // 곳(IP) → 틀린 시각들 (오래된 것부터)
  private final Map<String, Deque<Instant>> failsByPlace = new HashMap<>();
  // 모든 곳의 틀린 시각들 (오래된 것부터)
  private final Deque<Instant> allFails = new ArrayDeque<>();

  public DemoLoginLimiter(Clock clock) {
    this.clock = clock;
  }

  // 지금 이곳의 시도를 막아야 하는지. 막아야 하면 true 입니다.
  public synchronized boolean isBlocked(String place) {
    Instant now = clock.instant();
    dropOld(allFails, now);
    Deque<Instant> fails = failsByPlace.get(place);
    if (fails != null) {
      dropOld(fails, now);
      if (fails.isEmpty()) {
        failsByPlace.remove(place);
      }
    }
    int placeFails = fails == null ? 0 : fails.size();
    return placeFails >= MAX_FAILS_PER_PLACE || allFails.size() >= MAX_FAILS_TOTAL;
  }

  // 이곳에서 한 번 틀렸다고 적습니다.
  public synchronized void recordFail(String place) {
    Instant now = clock.instant();
    failsByPlace.computeIfAbsent(place, key -> new ArrayDeque<>()).addLast(now);
    allFails.addLast(now);
  }

  // 1분(WINDOW)보다 오래된 기록을 앞에서부터 지웁니다.
  private static void dropOld(Deque<Instant> fails, Instant now) {
    Instant from = now.minus(WINDOW);
    while (!fails.isEmpty() && !fails.peekFirst().isAfter(from)) {
      fails.removeFirst();
    }
  }
}
