package com.ithopeschool.projectbe.domain.job;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 연습 작업입니다. 예약 작업이 서버에서 정말 도는지 눈으로 확인하려고 만든 가장 단순한 작업입니다.
// 10분마다 불리면 서버 로그에 한 줄이 쌓입니다.
//
//   [연습 작업] 실행됐어요. 기준 날짜=2026-10-10, 시각=2026-10-10T08:20:03Z
//
// 진짜 작업(매일 밤 요약)도 같은 모양으로 만듭니다: ScheduledJob 을 구현한 @Component 하나면 됩니다.
@Component
@RequiredArgsConstructor
public class PracticeJob implements ScheduledJob {

  // 부르는 주소가 POST /api/v1/jobs/practice 가 됩니다.
  public static final String NAME = "practice";

  private static final Logger log = LoggerFactory.getLogger(PracticeJob.class);

  private final JobResultStore resultStore;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    // 확인용 로그 한 줄. (일기·기분 같은 개인 내용이나 비밀값은 로그에 절대 찍지 않습니다)
    log.info("[연습 작업] 실행됐어요. 기준 날짜={}, 시각={}", context.date(), context.startedAt());

    // 결과는 "그날 한 칸" 에 덮어씁니다. 같은 날 두 번 돌아도 칸이 늘지 않고 내용도 같습니다.
    // (결과 글자에 "몇 번째 실행" 같은 매번 달라지는 값을 넣지 않은 것도 같은 이유입니다)
    resultStore.save(NAME, context.date(), "연습 작업 결과: " + context.date());
  }
}
