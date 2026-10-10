package com.ithopeschool.projectbe.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// 서버 전체를 진짜로 켜고(가짜 없이) 연습 작업을 불러 봅니다. 앞의 테스트들이 부품을 따로 봤다면, 이건 이어 붙인 모습입니다.
//   - 문지기가 실제로 /api/v1/jobs/** 에 연결돼 있는지
//   - 로컬 키(application-local.yml 의 local-job-key)로 진짜 연습 작업이 도는지
//   - 두 번 불러도 결과가 망가지지 않는지
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class JobFlowTest {

  private static final String URL = "/api/v1/jobs/practice";
  private static final String LOCAL_KEY = "local-job-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private JobResultStore resultStore;
  @Autowired private Clock clock;

  @Test
  void rejectedRequestDoesNotRunTheJob(CapturedOutput output) throws Exception {
    int before = resultStore.size();

    mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post(URL).header("X-Job-Key", "not-the-key"))
        .andExpect(status().isUnauthorized());

    // 거절된 요청은 작업을 실행하지 않았고, 결과도 남기지 않았습니다.
    assertThat(resultStore.size()).isEqualTo(before);
    assertThat(output).doesNotContain("[연습 작업] 실행됐어요");
  }

  @Test
  void practiceJobRunsAndLeavesALogLine(CapturedOutput output) throws Exception {
    mockMvc
        .perform(post(URL).header("X-Job-Key", LOCAL_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.job").value("practice"))
        .andExpect(jsonPath("$.status").value("DONE"));

    // 완료 조건: 연습 작업이 돌면 서버 로그에 한 줄이 남습니다.
    assertThat(output).contains("[연습 작업] 실행됐어요");
  }

  @Test
  void callingTwiceKeepsTheResultSafe() throws Exception {
    LocalDate today = DayRule.dateOf(clock.instant());

    mockMvc.perform(post(URL).header("X-Job-Key", LOCAL_KEY)).andExpect(status().isOk());
    String afterFirst = resultStore.find("practice", today).orElseThrow();
    int sizeAfterFirst = resultStore.size();

    mockMvc.perform(post(URL).header("X-Job-Key", LOCAL_KEY)).andExpect(status().isOk());

    // 완료 조건: 같은 작업이 두 번 돌아도 결과가 망가지지 않습니다. (칸이 늘지 않고 내용도 같음)
    assertThat(resultStore.size()).isEqualTo(sizeAfterFirst);
    assertThat(resultStore.find("practice", today)).contains(afterFirst);
  }

  @Test
  void unknownJobWithTheRightKeyIs404() throws Exception {
    mockMvc
        .perform(post("/api/v1/jobs/nothing").header("X-Job-Key", LOCAL_KEY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
