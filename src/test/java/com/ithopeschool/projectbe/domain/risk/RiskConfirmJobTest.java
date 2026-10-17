package com.ithopeschool.projectbe.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// "위험 예측 확인" 예약 작업이 서비스를 부르고 결과를 그날 한 칸에 덮어쓰는지 확인합니다.
// 맞았는지를 어떻게 채우는지는 RiskServiceTest 가 확인합니다. 서비스는 가짜입니다.
class RiskConfirmJobTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
  // 새벽 5시(한국 시간) = 전날 20:00Z. 하루가 바뀐(4시) 뒤입니다.
  private static final JobContext EARLY_MORNING =
      new JobContext(TODAY, Instant.parse("2026-10-16T20:00:00Z"));

  private final RiskService riskService = mock(RiskService.class);
  private final JobResultStore resultStore = new JobResultStore();
  private final RiskConfirmJob job = new RiskConfirmJob(riskService, resultStore);

  @Test
  @DisplayName("작업 이름은 risk-confirm 이고, POST /api/v1/jobs/risk-confirm 으로 부른다")
  void hasTheNameInTheDocs() {
    assertThat(job.name()).isEqualTo("risk-confirm").isEqualTo(RiskConfirmJob.NAME);
  }

  @Test
  @DisplayName("오늘 날짜로 모든 학생의 비어 있는 예측을 채우고, 채운 개수를 그날 결과로 남긴다")
  void confirmsAllAndSavesTheCount() {
    given(riskService.confirmAll(TODAY)).willReturn(3);

    job.run(EARLY_MORNING);

    verify(riskService).confirmAll(TODAY);
    assertThat(resultStore.find(RiskConfirmJob.NAME, TODAY)).contains("맞았는지 채움 3개");
  }

  @Test
  @DisplayName("같은 작업이 두 번 돌아도 결과가 같은 자리에 덮어써진다 (두 번째는 채울 것이 없어 0개)")
  void runningTwiceOverwritesTheResult() {
    given(riskService.confirmAll(TODAY)).willReturn(3, 0);

    job.run(EARLY_MORNING);
    job.run(EARLY_MORNING);

    assertThat(resultStore.find(RiskConfirmJob.NAME, TODAY)).contains("맞았는지 채움 0개");
    assertThat(resultStore.size()).isEqualTo(1);
  }
}
