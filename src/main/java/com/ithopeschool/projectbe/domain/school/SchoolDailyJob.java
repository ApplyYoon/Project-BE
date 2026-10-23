package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 하루 1번, 우리 학생이 있는 학교의 정보를 미리 받아 두는 예약 작업입니다. (2026-10-18 "NEIS + 직접 크롤링", docs/JOBS.md)
//   POST /api/v1/jobs/school-daily  (cron-job.org 가 새벽에 부름)
//
// 학교마다 하는 일
//   1. NEIS 학사일정 새로 받기 — 받은 지 20시간이 안 됐으면 건너뜀 (SchoolEventService, school-events 작업과 같은 일)
//   2. NEIS 오늘 · 다음 수업일 급식 미리 받기 — 메모리에 6시간 (SchoolTodayService)
//   3. 학교 홈페이지 가정통신문 · 공지사항 제목 받기 — 메모리에 24시간 (SchoolNoticeService · SchoolHomepageCrawler)
//   시간표는 하지 않습니다. 반을 서버가 몰라서(반은 학생 기기에만 있음) 미리 받을 수 없습니다.
//
// ■ 같은 날 두 번 불려도 괜찮습니다. 이미 받은 학교는 홈페이지에 다시 가지 않고, NEIS 도 받은 지 오래되지 않았으면 부르지 않습니다.
// ■ cron-job.org 는 30초까지만 기다려서, 15초가 지나면 남은 학교는 다음 호출에 맡깁니다.
//   학교가 많아지면 cron 을 새벽 5시 ~ 6시에 10분마다 부르게 하면, 부를 때마다 남은 학교를 이어서 합니다.
// ■ 예의: 학교 홈페이지에 가는 학교 사이에는 1.5초 쉽니다. (학교 하나 안의 요청 규칙은 SchoolHomepageCrawler)
@Component
@RequiredArgsConstructor
public class SchoolDailyJob implements ScheduledJob {

  public static final String NAME = "school-daily";
  // 한 번 부를 때 쓰는 시간. 넘으면 남은 학교는 다음 호출에서 이어서 합니다.
  static final Duration TIME_BUDGET = Duration.ofSeconds(15);
  // 홈페이지에 가는 학교 사이에 쉬는 시간
  static final Duration SCHOOL_GAP = Duration.ofMillis(1500);

  private static final Logger log = LoggerFactory.getLogger(SchoolDailyJob.class);

  private final AccountRepository accountRepository;
  private final SchoolEventService schoolEventService;
  private final SchoolTodayService schoolTodayService;
  private final SchoolNoticeService schoolNoticeService;
  private final PoliteDelay delay;
  private final JobResultStore resultStore;
  private final Clock clock;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    List<String> schoolCodes = accountRepository.findDistinctSchoolCodes();
    Instant deadline = clock.instant().plus(TIME_BUDGET);
    int handled = 0;
    int withNotices = 0;
    boolean visitedHomepage = false;
    for (String schoolCode : schoolCodes) {
      if (clock.instant().isAfter(deadline)) {
        break;
      }
      schoolEventService.ensureFresh(schoolCode, SchoolEventService.JOB_REFRESH_AFTER);
      schoolTodayService.prefetchMeals(schoolCode);
      // 홈페이지에 가야 하는 학교면, 앞에서 다른 학교 홈페이지에 갔을 때 잠깐 쉽니다.
      if (!schoolNoticeService.isSettledToday(schoolCode)) {
        if (visitedHomepage) {
          delay.pause(SCHOOL_GAP);
        }
        visitedHomepage = true;
      }
      if (schoolNoticeService.refresh(schoolCode)) {
        withNotices++;
      }
      handled++;
    }
    int left = schoolCodes.size() - handled;
    // 학교 번호 같은 내용은 로그에 찍지 않고 개수만 남깁니다.
    log.info(
        "[학교 하루 작업] 학교 {}곳 중 {}곳을 처리했어요. 공지를 받아 둔 학교 {}곳, 다음에 할 학교 {}곳",
        schoolCodes.size(),
        handled,
        withNotices,
        left);
    resultStore.save(
        NAME,
        context.date(),
        "학교 "
            + schoolCodes.size()
            + "곳 중 "
            + handled
            + "곳 처리 · 공지 "
            + withNotices
            + "곳 · 남은 "
            + left
            + "곳");
  }
}
