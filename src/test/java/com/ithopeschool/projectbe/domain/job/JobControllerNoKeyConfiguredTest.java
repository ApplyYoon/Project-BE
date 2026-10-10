package com.ithopeschool.projectbe.domain.job;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 서버에 비밀 키를 설정하는 것을 깜빡한 상황입니다. (배포 서버에 JOB_SECRET_KEY 를 안 넣은 경우)
// "키가 없으니 아무나 통과" 가 아니라 "키가 없으니 아무도 통과 못 함" 이어야 안전합니다.
@WebMvcTest(controllers = JobController.class, properties = "app.job.secret-key=")
@Import({JobWebConfig.class, JobKeyInterceptor.class})
class JobControllerNoKeyConfiguredTest {

  private static final String URL = "/api/v1/jobs/practice";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JobRunner jobRunner;

  @Test
  void rejectsRequestWithoutKey() throws Exception {
    mockMvc
        .perform(post(URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

    verify(jobRunner, never()).run(anyString());
  }

  // 빈 값("")끼리 같다고 통과시키면 안 됩니다. 헤더를 비워 보내도, 아무 값을 보내도 전부 거절입니다.
  @ParameterizedTest
  @ValueSource(strings = {"", " ", "anything", "local-job-key"})
  void rejectsEveryKeyWhenServerHasNoKey(String key) throws Exception {
    mockMvc.perform(post(URL).header("X-Job-Key", key)).andExpect(status().isUnauthorized());

    verify(jobRunner, never()).run(anyString());
  }
}
