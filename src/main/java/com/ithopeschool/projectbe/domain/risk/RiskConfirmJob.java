package com.ithopeschool.projectbe.domain.risk;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 하루 한 번, 그날이 지난 위험 예측에 실제 달성률과 "맞았는지" 를 채우는 예약 작업입니다. (이슈 #49, docs/JOBS.md "risk-confirm")
// cron-job.org 가 POST /api/v1/jobs/risk-confirm 을 부릅니다. (예: 매일 새벽 5시 — 하루가 바뀐(4시) 뒤)
//
// 어제까지 지난 날 중, 최근 14일 안에서 아직 비어 있는 예측을 모든 학생 것으로 채웁니다. (RiskService.confirmAll)
//   - 그날 계획이 없어서 달성률을 구할 수 없던 예측은 채우지 않고 비워 둡니다.
//   - 이미 채운 예측은 건드리지 않습니다. 그래서 같은 작업이 두 번 돌아도 결과가 같습니다.
// 학생이 새로 예측을 할 때도 그 학생의 것은 함께 채워서, 이 작업이 늦어도 빠지는 예측이 없습니다.
//
// ⚠️ 예측의 내용이나 학생의 기록은 로그에 찍지 않습니다. 몇 개를 채웠는지 숫자만 남깁니다.
@Component
@RequiredArgsConstructor
public class RiskConfirmJob implements ScheduledJob {

  public static final String NAME = "risk-confirm";

  private static final Logger log = LoggerFactory.getLogger(RiskConfirmJob.class);

  private final RiskService riskService;
  private final JobResultStore resultStore;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    int confirmed = riskService.confirmAll(context.date());
    // 결과는 그날 한 칸에 덮어씁니다. (같은 날 다시 돌면 채울 것이 없어서 0개로 덮어써집니다)
    resultStore.save(NAME, context.date(), "맞았는지 채움 " + confirmed + "개");
    log.info("[위험 예측 확인] 맞았는지 채움 {}개", confirmed);
  }
}
