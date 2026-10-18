package com.ithopeschool.projectbe.domain.goal;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 주가 끝난 "이번 주 목표" 의 달성 여부를 기록으로 판정해 적는 예약 작업입니다. (이슈 #88, docs/JOBS.md "weekly-goals")
// cron-job.org 가 POST /api/v1/jobs/weekly-goals 를 부릅니다. (예: 매주 월요일 새벽 5시 — 한 주가 끝난(월요일 4시) 뒤)
//
// 지난 일요일까지 끝난 주 중 아직 판정하지 않은 목표를 모든 학생 것으로 판정합니다. (WeekGoalService.judgeEndedWeeks)
//   - 이미 판정한 목표는 건드리지 않습니다. 그래서 같은 작업이 두 번 돌아도 결과가 같습니다.
//   - 작업이 한 주 늦게 돌아도 빠지는 목표가 없습니다. (끝난 주라면 몇 주 전 것이든 판정 전이면 채움)
//
// ⚠️ 학생의 목표나 기록은 로그에 찍지 않습니다. 몇 개를 판정했는지 숫자만 남깁니다.
@Component
@RequiredArgsConstructor
public class WeekGoalJudgeJob implements ScheduledJob {

  public static final String NAME = "weekly-goals";

  private static final Logger log = LoggerFactory.getLogger(WeekGoalJudgeJob.class);

  private final WeekGoalService weekGoalService;
  private final JobResultStore resultStore;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    int judged = weekGoalService.judgeEndedWeeks(context.date());
    // 결과는 그날 한 칸에 덮어씁니다. (같은 날 다시 돌면 판정할 것이 없어서 0개로 덮어써집니다)
    resultStore.save(NAME, context.date(), "주 목표 판정 " + judged + "개");
    log.info("[주 목표 판정] {}개", judged);
  }
}
