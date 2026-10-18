package com.ithopeschool.projectbe.domain.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 하루 1번 학교 작업 테스트. NEIS · 홈페이지는 가짜이고, 쉬기(PoliteDelay)도 기다리지 않는 가짜입니다.
class SchoolDailyJobTest {

  private static final Instant NOW = Instant.parse("2026-10-17T20:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final SchoolEventService schoolEventService = mock(SchoolEventService.class);
  private final SchoolTodayService schoolTodayService = mock(SchoolTodayService.class);
  private final SchoolNoticeService schoolNoticeService = mock(SchoolNoticeService.class);
  private final PoliteDelay delay = mock(PoliteDelay.class);
  private final JobResultStore resultStore = new JobResultStore();
  private final Clock clock = mock(Clock.class);
  private final SchoolDailyJob job =
      new SchoolDailyJob(
          accountRepository,
          schoolEventService,
          schoolTodayService,
          schoolNoticeService,
          delay,
          resultStore,
          clock);

  @BeforeEach
  void setUp() {
    given(clock.instant()).willReturn(NOW);
  }

  @Test
  @DisplayName("학교마다 학사일정 · 급식 · 홈페이지 공지를 받고, 홈페이지에 가는 학교 사이에만 쉰다")
  void handlesEverySchool() {
    given(accountRepository.findDistinctSchoolCodes()).willReturn(List.of("A", "B", "C"));
    // B 는 오늘 이미 받아 둔 학교라 홈페이지에 다시 가지 않습니다.
    given(schoolNoticeService.isSettledToday("B")).willReturn(true);
    given(schoolNoticeService.refresh("A")).willReturn(true);
    given(schoolNoticeService.refresh("B")).willReturn(true);
    given(schoolNoticeService.refresh("C")).willReturn(false);

    job.run(new JobContext(TODAY, NOW));

    for (String school : List.of("A", "B", "C")) {
      verify(schoolEventService).ensureFresh(school, SchoolEventService.JOB_REFRESH_AFTER);
      verify(schoolTodayService).prefetchMeals(school);
      verify(schoolNoticeService).refresh(school);
    }
    // 홈페이지에 가는 학교는 A, C → 그 사이에 한 번만 쉽니다.
    verify(delay, times(1)).pause(SchoolDailyJob.SCHOOL_GAP);
    assertThat(resultStore.find(SchoolDailyJob.NAME, TODAY))
        .contains("학교 3곳 중 3곳 처리 · 공지 2곳 · 남은 0곳");
  }

  @Test
  @DisplayName("정한 시간이 지나면 남은 학교는 다음 호출에 맡긴다")
  void stopsAtTimeBudget() {
    given(accountRepository.findDistinctSchoolCodes()).willReturn(List.of("A", "B", "C"));
    // 첫 학교를 시작할 때까지는 그대로, 그다음부터는 16초가 지난 것처럼
    given(clock.instant())
        .willReturn(NOW, NOW, NOW.plus(SchoolDailyJob.TIME_BUDGET).plusSeconds(1));

    job.run(new JobContext(TODAY, NOW));

    verify(schoolNoticeService).refresh("A");
    verify(schoolNoticeService, never()).refresh("B");
    verify(schoolNoticeService, never()).refresh("C");
    assertThat(resultStore.find(SchoolDailyJob.NAME, TODAY))
        .contains("학교 3곳 중 1곳 처리 · 공지 0곳 · 남은 2곳");
  }

  @Test
  @DisplayName("우리 학생이 고른 학교가 없으면 아무 데도 가지 않는다")
  void noSchools() {
    given(accountRepository.findDistinctSchoolCodes()).willReturn(List.of());

    job.run(new JobContext(TODAY, NOW));

    verify(schoolNoticeService, never()).refresh(anyString());
    verify(delay, never()).pause(any());
    assertThat(job.name()).isEqualTo("school-daily");
  }
}
