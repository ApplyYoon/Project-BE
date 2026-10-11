package com.ithopeschool.projectbe.domain.coach;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 한 사람이 하루에 AI 를 부를 수 있는 횟수를 세는 곳입니다. GPT 는 부를 때마다 돈이 나가기 때문입니다.
// 버그 하나나 장난 요청으로 예산이 사라지지 않게, 한도를 넘으면 그날은 더 부르지 않습니다.
//
// ⚠️ 횟수는 서버의 메모리에만 있습니다. 서버가 꺼졌다 켜지면 0 으로 돌아갑니다.
//    제대로 된 비용 지키기(호출 기록을 DB 에 남기기, 통화 시간 제한)는 이슈 #33 에서 만듭니다.
//    그때까지 OpenAI 계정에도 월 사용 한도를 꼭 걸어 두세요. (docs/DEPLOY.md)
@Component
public class CoachDailyLimit {

  // 한 사람이 하루에 부를 수 있는 횟수. 설정 app.ai.daily-limit-per-user 에서 받습니다.
  private final int limitPerDay;
  private final Clock clock;

  // 지금 세고 있는 날짜와, 그날 사용자별로 부른 횟수 (사용자 번호 → 횟수)
  private LocalDate countingDate;
  private final Map<Long, Integer> usedCounts = new HashMap<>();

  public CoachDailyLimit(@Value("${app.ai.daily-limit-per-user:20}") int limitPerDay, Clock clock) {
    this.limitPerDay = limitPerDay;
    this.clock = clock;
  }

  // 한 번 써도 되는지 확인하고, 되면 횟수를 하나 올립니다. 한도를 넘었으면 false 입니다.
  // synchronized: 여러 요청이 동시에 와도 한 번에 하나씩만 이 메서드에 들어옵니다. (횟수가 꼬이지 않게)
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
}
