package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 한 사람이 하루에 AI 통화를 시작할 수 있는 횟수를 세는 곳입니다. 음성 통화는 분 단위로 돈이 나가기 때문입니다.
// 버그 하나나 장난으로 통화를 계속 걸어 예산이 사라지지 않게, 한도를 넘으면 그날은 더 걸 수 없습니다.
//
// ⚠️ 횟수는 서버의 메모리에만 있습니다. 서버가 꺼졌다 켜지면 0 으로 돌아갑니다.
//    주간 코치의 CoachDailyLimit 과 같은 방식이고, 제대로 된 비용 지키기(DB 기록·통화 시간 합계)는 이슈 #33 에서 합칩니다.
@Component
public class CallDailyLimit {

  // 한 사람이 하루에 걸 수 있는 통화 수. 설정 app.ai.call-daily-limit-per-user 에서 받습니다.
  private final int limitPerDay;
  private final Clock clock;

  // 지금 세고 있는 날짜와, 그날 사용자별로 건 횟수 (사용자 번호 → 횟수)
  private LocalDate countingDate;
  private final Map<Long, Integer> usedCounts = new HashMap<>();

  public CallDailyLimit(
      @Value("${app.ai.call-daily-limit-per-user:3}") int limitPerDay, Clock clock) {
    this.limitPerDay = limitPerDay;
    this.clock = clock;
  }

  // 한 번 걸어도 되는지 확인하고, 되면 횟수를 하나 올립니다. 한도를 넘었으면 false 입니다.
  // synchronized: 여러 요청이 동시에 와도 한 번에 하나씩만 들어옵니다. (횟수가 꼬이지 않게)
  public synchronized boolean tryUse(Long userId) {
    // 날짜는 "하루의 기준(새벽 4시, 한국 시간)" 을 따릅니다. 날이 바뀌면 어제의 횟수를 모두 지웁니다.
    LocalDate today = DayRule.dateOf(Instant.now(clock));
    if (!today.equals(countingDate)) {
      countingDate = today;
      usedCounts.clear();
    }

    int used = usedCounts.getOrDefault(userId, 0);
    if (used >= limitPerDay) {
      return false;
    }
    usedCounts.put(userId, used + 1);
    return true;
  }

  // 방금 올린 횟수를 하나 되돌립니다. 임시 키를 받지 못해 통화가 시작되지 않았을 때 씁니다.
  public synchronized void giveBack(Long userId) {
    int used = usedCounts.getOrDefault(userId, 0);
    if (used > 0) {
      usedCounts.put(userId, used - 1);
    }
  }
}
