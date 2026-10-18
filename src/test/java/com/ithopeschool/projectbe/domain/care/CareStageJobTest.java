package com.ithopeschool.projectbe.domain.care;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 예약 작업 care-stages: 학생마다 판정을 부르고, 새로 판정한 사람 수만 결과로 남기는지 확인합니다.
class CareStageJobTest {

  @Test
  @DisplayName("온보딩을 마친 학생마다 판정하고, 이미 판정한 학생은 세지 않는다")
  void decidesEachStudent() {
    CareStageService service = mock(CareStageService.class);
    JobResultStore resultStore = mock(JobResultStore.class);
    LocalDate today = LocalDate.of(2026, 10, 18);
    given(service.onboardedUserIds()).willReturn(List.of(1L, 2L));
    given(service.decideFor(1L, today)).willReturn(true);
    given(service.decideFor(2L, today)).willReturn(false);

    new CareStageJob(service, resultStore).run(new JobContext(today, Instant.EPOCH));

    verify(service).decideFor(1L, today);
    verify(service).decideFor(2L, today);
    verify(resultStore).save(CareStageJob.NAME, today, "관리 단계 판정 1명");
  }
}
