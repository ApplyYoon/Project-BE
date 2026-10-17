package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 하루 1번 학교 학사일정을 새로 받는 예약 작업입니다. (이슈 #282, docs/JOBS.md)
//   POST /api/v1/jobs/school-events  (cron-job.org 가 매일 새벽 5시에 부름)
// 우리 학생이 고른 학교만 받습니다. 받은 지 20시간이 안 된 학교는 건너뜁니다.
// 같은 날 두 번 돌아도 결과가 같습니다. (학교마다 그 기간 일정을 통째로 바꿔 넣음)
@Component
@RequiredArgsConstructor
public class SchoolEventsJob implements ScheduledJob {

  public static final String NAME = "school-events";

  private static final Logger log = LoggerFactory.getLogger(SchoolEventsJob.class);

  private final AccountRepository accountRepository;
  private final SchoolEventService schoolEventService;
  private final JobResultStore resultStore;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    List<String> schoolCodes = accountRepository.findDistinctSchoolCodes();
    int fresh = 0;
    for (String schoolCode : schoolCodes) {
      if (schoolEventService
          .ensureFresh(schoolCode, SchoolEventService.JOB_REFRESH_AFTER)
          .isPresent()) {
        fresh++;
      }
    }
    // 학교 번호 같은 내용은 로그에 찍지 않고 개수만 남깁니다.
    log.info("[학사일정] 학교 {}곳 중 {}곳의 일정이 최신이에요.", schoolCodes.size(), fresh);
    resultStore.save(NAME, context.date(), "학교 " + schoolCodes.size() + "곳 중 " + fresh + "곳 최신");
  }
}
