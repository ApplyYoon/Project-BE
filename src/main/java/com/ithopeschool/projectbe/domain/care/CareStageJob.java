package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 매일 새벽, 온보딩을 마친 모든 학생의 오늘 관리 단계를 판정해 두는 예약 작업입니다. (이슈 #62, docs/JOBS.md "care-stages")
// cron-job.org 가 POST /api/v1/jobs/care-stages 를 부릅니다. (예: 매일 새벽 4시 10분 — 하루가 바뀐(4시) 뒤)
//
// 앱을 열지 않은 날도 단계가 남아야 "당근 주 vs 채찍 주" 비교(#47)를 할 수 있어서 만듭니다.
// 이미 그날 단계가 있는 학생은 건너뜁니다. 그래서 같은 작업이 두 번 돌아도 결과가 같습니다.
// 학생마다 따로 저장해서(decideFor 의 @Transactional), 중간에 실패해도 앞의 학생 것은 남고 다시 돌리면 나머지만 합니다.
//
// ⚠️ 단계의 이유 문장이나 학생의 기록은 로그에 찍지 않습니다. 몇 명을 판정했는지 숫자만 남깁니다.
@Component
@RequiredArgsConstructor
public class CareStageJob implements ScheduledJob {

  public static final String NAME = "care-stages";

  private static final Logger log = LoggerFactory.getLogger(CareStageJob.class);

  private final CareStageService careStageService;
  private final JobResultStore resultStore;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    int decided = 0;
    for (Long userId : careStageService.onboardedUserIds()) {
      if (careStageService.decideFor(userId, context.date())) {
        decided++;
      }
    }
    // 결과는 그날 한 칸에 덮어씁니다. (같은 날 다시 돌면 새로 판정할 학생이 없어서 0명으로 덮어써집니다)
    resultStore.save(NAME, context.date(), "관리 단계 판정 " + decided + "명");
    log.info("[관리 단계] 판정 {}명", decided);
  }
}
