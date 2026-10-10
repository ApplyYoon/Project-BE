package com.ithopeschool.projectbe.domain.job;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 예약 작업 API 를 확인합니다. 특히 "아무나 작업 주소를 불러 실행시킬 수 없다(비밀 키 확인)" 를 꼼꼼히 봅니다.
// 서버가 설정한 키는 test-secret 입니다. (properties 로 넣었습니다)
@WebMvcTest(controllers = JobController.class, properties = "app.job.secret-key=test-secret")
// 문지기(키 확인)와 그 연결 설정도 함께 올려야 실제와 같은 조건으로 시험할 수 있습니다.
@Import({JobWebConfig.class, JobKeyInterceptor.class})
class JobControllerTest {

  private static final String URL = "/api/v1/jobs/practice";

  @Autowired private MockMvc mockMvc;

  // 진짜 실행기 대신 가짜를 끼웁니다. 키 확인이 통과하지 못하면 이 가짜는 절대 불리지 않아야 합니다.
  @MockitoBean private JobRunner jobRunner;

  private static JobRunResult result(JobRunStatus status) {
    return new JobRunResult(
        "practice", status, LocalDate.of(2026, 10, 10), Instant.parse("2026-10-10T08:20:03Z"));
  }

  @Test
  void rejectsRequestWithoutKey() throws Exception {
    mockMvc
        .perform(post(URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("작업 키가 없거나 맞지 않아요"));

    // 작업은 실행되지 않았어야 합니다.
    verify(jobRunner, never()).run(anyString());
  }

  @Test
  void rejectsRequestWithWrongKey() throws Exception {
    mockMvc
        .perform(post(URL).header("X-Job-Key", "wrong-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

    verify(jobRunner, never()).run(anyString());
  }

  // 거의 맞는 키들도 전부 거절해야 합니다. (대소문자, 공백, 길이가 다른 경우, 빈 값)
  @ParameterizedTest
  @ValueSource(
      strings = {
        "TEST-SECRET",
        "test-secret ",
        " test-secret",
        "test-secre",
        "test-secret-extra",
        "test-secret\n",
        ""
      })
  void rejectsNearMissKeys(String nearMiss) throws Exception {
    mockMvc.perform(post(URL).header("X-Job-Key", nearMiss)).andExpect(status().isUnauthorized());

    verify(jobRunner, never()).run(anyString());
  }

  @Test
  void rejectsWhenKeyIsSentInTheWrongPlace() throws Exception {
    // 헤더가 아니라 주소(쿼리)나 다른 헤더에 키를 넣어도 통과하면 안 됩니다.
    mockMvc.perform(post(URL).param("key", "test-secret")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post(URL).header("Authorization", "Bearer test-secret"))
        .andExpect(status().isUnauthorized());

    verify(jobRunner, never()).run(anyString());
  }

  @Test
  void doesNotRevealWhichJobsExistWhenKeyIsMissing() throws Exception {
    // 키가 없으면 없는 작업(404)이 아니라 똑같이 401 입니다. 어떤 작업이 있는지 새어 나가지 않게 하려는 것입니다.
    mockMvc.perform(post("/api/v1/jobs/does-not-exist")).andExpect(status().isUnauthorized());
  }

  @Test
  void protectsEveryAddressUnderJobs() throws Exception {
    // 앞으로 작업 주소가 늘어나도(예: /api/v1/jobs/anything/else) 키 확인을 빼먹을 수 없습니다.
    mockMvc.perform(post("/api/v1/jobs/a/b/c")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/jobs")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/jobs")).andExpect(status().isUnauthorized());
  }

  @Test
  void disguisedAddressesAreStillProtected() throws Exception {
    // 주소를 살짝 바꿔서 문지기를 피하려는 시도입니다. 문지기가 Controller 와 같은 방식으로 주소를 읽어서 전부 막힙니다.
    // 1) 글자를 %6A 로 인코딩한 주소 (%6A = j)
    mockMvc
        .perform(post(URI.create("/api/v1/%6Aobs/practice")))
        .andExpect(status().isUnauthorized());
    // 2) 주소 중간에 ;x=1 같은 군더더기를 붙인 주소
    mockMvc
        .perform(post(URI.create("/api/v1/jobs;x=1/practice")))
        .andExpect(status().isUnauthorized());
    // 3) 끝에 / 를 붙인 주소
    mockMvc.perform(post("/api/v1/jobs/practice/")).andExpect(status().isUnauthorized());
    // 4) / 를 겹쳐 쓴 주소
    mockMvc.perform(post("/api/v1//jobs//practice")).andExpect(status().isUnauthorized());

    // 어느 것도 작업을 실행하지 못했어야 합니다.
    verify(jobRunner, never()).run(anyString());
  }

  @Test
  void otherMethodsNeverRunTheJob() throws Exception {
    // POST 가 아닌 방법은 "허용하지 않는 방법"(405) 으로 끝납니다. 키가 있어도 작업은 실행되지 않습니다.
    mockMvc.perform(get(URL)).andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(get(URL).header("X-Job-Key", "test-secret"))
        .andExpect(status().isMethodNotAllowed());
    mockMvc.perform(delete(URL)).andExpect(status().isMethodNotAllowed());

    verify(jobRunner, never()).run(anyString());
  }

  @Test
  void runsTheJobWithTheRightKey() throws Exception {
    given(jobRunner.run("practice")).willReturn(Optional.of(result(JobRunStatus.DONE)));

    mockMvc
        .perform(post(URL).header("X-Job-Key", "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.job").value("practice"))
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.date").value("2026-10-10"))
        // 시각은 따옴표 없는 밀리초 숫자입니다. (docs/API.md 2-2)
        .andExpect(jsonPath("$.ranAt").value(1791620403000L))
        .andExpect(jsonPath("$.message").value("작업을 실행했어요"));
  }

  @Test
  void headerNameIsCaseInsensitive() throws Exception {
    given(jobRunner.run("practice")).willReturn(Optional.of(result(JobRunStatus.DONE)));

    // HTTP 헤더 이름은 대소문자를 가리지 않습니다. (스케줄러가 소문자로 보내도 통과해야 합니다)
    mockMvc.perform(post(URL).header("x-job-key", "test-secret")).andExpect(status().isOk());
  }

  @Test
  void answersSkippedWithOkWhenAlreadyRunning() throws Exception {
    given(jobRunner.run("practice")).willReturn(Optional.of(result(JobRunStatus.SKIPPED)));

    // 건너뛴 것은 에러가 아닙니다. 200 으로 답해야 스케줄러가 "실패" 로 오해하지 않습니다.
    mockMvc
        .perform(post(URL).header("X-Job-Key", "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SKIPPED"))
        .andExpect(jsonPath("$.message").value("같은 작업이 이미 실행 중이라 건너뛰었어요"));
  }

  @Test
  void answers404ForUnknownJobWithTheRightKey() throws Exception {
    given(jobRunner.run("nothing")).willReturn(Optional.empty());

    mockMvc
        .perform(post("/api/v1/jobs/nothing").header("X-Job-Key", "test-secret"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("그런 작업이 없어요"));
  }

  @Test
  void answers500WhenTheJobFailedWithoutLeakingDetails() throws Exception {
    given(jobRunner.run("practice")).willReturn(Optional.of(result(JobRunStatus.FAILED)));

    // 실패는 500 으로 알려야 스케줄러가 알아채고 이메일을 보냅니다. 내부 정보는 담지 않습니다.
    mockMvc
        .perform(post(URL).header("X-Job-Key", "test-secret"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.error.message").value("작업이 실패했어요"));
  }

  @Test
  void rejectionBodyNeverContainsTheSecret() throws Exception {
    mockMvc
        .perform(post(URL).header("X-Job-Key", "wrong-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-secret"))))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("wrong-key"))));
  }
}
