package com.ithopeschool.projectbe.domain.job;

// 예약 작업 하나의 "약속"입니다. 새 작업(매일 밤 요약 등)을 만들 때는 이 약속을 지키는 클래스를 만듭니다.
//
//   @Component
//   public class DailySummaryJob implements ScheduledJob { ... }
//
// ⚠️ 가장 중요한 규칙: **같은 작업이 두 번 돌아도 결과가 망가지면 안 됩니다.** (docs/JOBS.md)
//   - 스케줄러가 실수로 두 번 부르거나, 실패해서 다시 부를 수 있습니다.
//   - 그래서 결과는 "새로 추가" 가 아니라 "그날 것을 덮어쓰기" 로 저장합니다.
//   - 같은 날 요약을 다시 만들면 같은 결과가 같은 자리에 덮어써져야 합니다.
public interface ScheduledJob {

  // 작업의 이름입니다. 부르는 주소 POST /api/v1/jobs/{이름} 에 그대로 쓰입니다. (소문자·숫자·하이픈)
  String name();

  // 작업 한 번을 실행합니다. 실패하면 예외를 던지면 됩니다. (실행기가 로그를 남기고 "실패" 로 답합니다)
  // 부르는 쪽(cron-job.org)은 30초까지만 기다리므로, 오래 걸리는 일은 한 번에 하지 말고 나눠서 하세요.
  void run(JobContext context);
}
