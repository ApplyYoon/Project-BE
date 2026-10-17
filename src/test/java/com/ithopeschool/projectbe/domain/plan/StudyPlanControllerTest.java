package com.ithopeschool.projectbe.domain.plan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanBatchCreateRequest;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanCreateRequest;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanResponse;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanUpdateRequest;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(StudyPlanService)은 가짜로 바꿉니다.
@WebMvcTest(StudyPlanController.class)
class StudyPlanControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StudyPlanService studyPlanService;

  private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);

  private static final StudyPlanResponse MATH =
      new StudyPlanResponse("7", OCT_5, "3", "수학 3단원", 60, false, "19:00", "study", null);

  // 계획 추가 요청 한 개의 JSON. 바꾸고 싶은 칸만 뒤에 덧붙여 씁니다.
  private static final String VALID_PLAN =
      "\"date\":\"2026-10-05\",\"subjectId\":\"3\",\"content\":\"수학\",\"targetMinutes\":60";

  private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(studyPlanService);
  }

  // ---- 목록 ----

  @Test
  @DisplayName("목록은 200 이고, 계획마다 FE StudyPlan 의 아홉 칸이 있다")
  void getPlans() throws Exception {
    given(studyPlanService.getPlans(OCT_5))
        .willReturn(
            List.of(
                new StudyPlanResponse(
                    "7", OCT_5, "3", "수학 문제집 3단원 풀기", 90, false, null, "study", null)));

    mockMvc
        .perform(get("/api/v1/study-plans").param("date", "2026-10-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("7"))
        .andExpect(jsonPath("$[0].date").value("2026-10-05"))
        .andExpect(jsonPath("$[0].subjectId").value("3"))
        .andExpect(jsonPath("$[0].content").value("수학 문제집 3단원 풀기"))
        .andExpect(jsonPath("$[0].targetMinutes").value(90))
        .andExpect(jsonPath("$[0].done").value(false))
        .andExpect(jsonPath("$[0].kind").value("study"))
        // 값이 없는 칸은 빼지 않고 null 로 보냅니다. (docs/API.md 4장)
        .andExpect(jsonPath("$[0].startTime").isEmpty())
        .andExpect(jsonPath("$[0].routineId").isEmpty())
        .andExpect(jsonPath("$[0].length()").value(9))
        .andExpect(jsonPath("$[0].userId").doesNotExist());
  }

  @Test
  @DisplayName("계획이 없는 날은 404 가 아니라 빈 배열 []")
  void emptyDay() throws Exception {
    given(studyPlanService.getPlans(OCT_5)).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/study-plans").param("date", "2026-10-05"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));
  }

  @Test
  @DisplayName("날짜를 안 보내면 400 이고, Service 는 부르지 않는다")
  void missingDate() throws Exception {
    mockMvc
        .perform(get("/api/v1/study-plans"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(studyPlanService);
  }

  @Test
  @DisplayName("from · to 를 함께 보내면 기간 목록을 돌려준다 (#250)")
  void getPlansBetween() throws Exception {
    given(studyPlanService.getPlansBetween(OCT_5, OCT_5.plusDays(1)))
        .willReturn(
            List.of(
                new StudyPlanResponse("7", OCT_5, "3", "수학", 60, false, null, "study", null),
                new StudyPlanResponse(
                    "8", OCT_5.plusDays(1), "3", "영어", 30, true, "19:00", "study", null)));

    mockMvc
        .perform(get("/api/v1/study-plans").param("from", "2026-10-05").param("to", "2026-10-06"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].date").value("2026-10-05"))
        .andExpect(jsonPath("$[1].date").value("2026-10-06"))
        .andExpect(jsonPath("$[1].startTime").value("19:00"));
  }

  @ParameterizedTest(name = "{0} 이면 400")
  @ValueSource(
      strings = {
        "date=2026-10-05&from=2026-10-01&to=2026-10-05",
        "from=2026-10-01",
        "to=2026-10-05",
        "from=2026/10/01&to=2026-10-05",
        "from=2026-10-01&to=abc"
      })
  @DisplayName("date 와 from · to 를 섞거나, 하나만 보내거나, 모양이 틀리면 400 이고 Service 는 부르지 않는다")
  void wrongRangeParams(String query) throws Exception {
    mockMvc
        .perform(get("/api/v1/study-plans?" + query))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(studyPlanService);
  }

  @Test
  @DisplayName("섞어 보내면 무엇을 보내야 하는지 알려 준다")
  void mixedParamsMessage() throws Exception {
    mockMvc
        .perform(get("/api/v1/study-plans").param("date", "2026-10-05").param("from", "2026-10-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("date 하나, 또는 from 과 to 를 함께 보내 주세요"));
  }

  @ParameterizedTest(name = "date={0} 이면 400")
  @ValueSource(strings = {"2026/10/05", "2026-10-5", "2026-13-01", "10월 5일", "", "abc"})
  @DisplayName("날짜 모양이 틀리면 400 이고, Service 는 부르지 않는다")
  void wrongDateFormat(String date) throws Exception {
    mockMvc
        .perform(get("/api/v1/study-plans").param("date", date))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(studyPlanService);
  }

  @Test
  @DisplayName("로그인하지 않았으면 Service 가 던진 401 이 그대로 나간다")
  void unauthorized() throws Exception {
    given(studyPlanService.getPlans(OCT_5))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/study-plans").param("date", "2026-10-05"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("로그인이 필요해요"));
  }

  // ---- 추가 ----

  @Test
  @DisplayName("추가는 201 이고 만들어진 계획을 돌려준다")
  void create() throws Exception {
    given(studyPlanService.create(any())).willReturn(MATH);

    send(
            post("/api/v1/study-plans"),
            """
            {"date":"2026-10-05","subjectId":"3","content":"수학 3단원","targetMinutes":60,
             "startTime":"19:00","kind":"study"}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("7"))
        .andExpect(jsonPath("$.startTime").value("19:00"))
        .andExpect(jsonPath("$.kind").value("study"));
    verify(studyPlanService)
        .create(new StudyPlanCreateRequest(OCT_5, "3", "수학 3단원", 60, "19:00", "study"));
  }

  @Test
  @DisplayName("할 일의 앞뒤 공백은 지우고 넘긴다")
  void createTrimsContent() throws Exception {
    given(studyPlanService.create(any())).willReturn(MATH);

    send(
            post("/api/v1/study-plans"),
            "{\"date\":\"2026-10-05\",\"subjectId\":\"3\",\"content\":\"  수학  \",\"targetMinutes\":60}")
        .andExpect(status().isCreated());
    verify(studyPlanService).create(new StudyPlanCreateRequest(OCT_5, "3", "수학", 60, null, null));
  }

  // { 보낼 JSON, 돌아와야 할 문장 }
  static Stream<Arguments> wrongCreateRequests() {
    return Stream.of(
        Arguments.of("{\"subjectId\":\"3\",\"content\":\"수학\",\"targetMinutes\":60}", "날짜를 보내 주세요"),
        Arguments.of(
            "{\"date\":\"2026-10-05\",\"subjectId\":\"3\",\"content\":\"   \",\"targetMinutes\":60}",
            "할 일을 입력해 주세요"),
        Arguments.of(
            "{\"date\":\"2026-10-05\",\"subjectId\":\"3\",\"targetMinutes\":60}", "할 일을 입력해 주세요"),
        Arguments.of(
            "{\"date\":\"2026-10-05\",\"subjectId\":\"3\",\"content\":\"수학\"}", "목표 시간을 보내 주세요"),
        Arguments.of("{" + VALID_PLAN.replace("60", "0") + "}", "목표 시간은 1분 이상이어야 해요"),
        Arguments.of("{" + VALID_PLAN.replace("60", "601") + "}", "목표 시간은 600분(10시간) 이하여야 해요"),
        Arguments.of("{" + VALID_PLAN + ",\"startTime\":\"25:00\"}", "시작 시각은 19:00 처럼 보내 주세요"),
        Arguments.of("{" + VALID_PLAN + ",\"startTime\":\"7:00\"}", "시작 시각은 19:00 처럼 보내 주세요"),
        Arguments.of("{" + VALID_PLAN.replace("\"3\"", "\"abc\"") + "}", "과목 번호가 올바르지 않아요"),
        // 19자리는 서버의 숫자(Long)에 안 들어갈 수 있어서 400 으로 막습니다. (예전에는 500 이 났음)
        Arguments.of(
            "{" + VALID_PLAN.replace("\"3\"", "\"9999999999999999999\"") + "}", "과목 번호가 올바르지 않아요"),
        Arguments.of("{" + VALID_PLAN.replace("수학", "가".repeat(201)) + "}", "할 일은 200자 이하로 적어 주세요"),
        Arguments.of(
            "{" + VALID_PLAN.replace("2026-10-05", "2026/10/05") + "}", "보낸 값을 다시 확인해 주세요"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("wrongCreateRequests")
  @DisplayName("추가 요청이 틀리면 400 이고, Service 는 부르지 않는다")
  void createRejected(String body, String message) throws Exception {
    expectRejected(send(post("/api/v1/study-plans"), body), message);
  }

  @Test
  @DisplayName("내 과목이 아니면 Service 가 던진 404 가 그대로 나간다")
  void createWithOthersSubject() throws Exception {
    given(studyPlanService.create(any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요"));

    send(post("/api/v1/study-plans"), "{" + VALID_PLAN + "}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("과목을 찾을 수 없어요"));
  }

  // ---- 여러 개 추가 ----

  @Test
  @DisplayName("여러 개 추가는 201 이고 만들어진 계획의 배열을 돌려준다")
  void createAll() throws Exception {
    given(studyPlanService.createAll(any())).willReturn(List.of(MATH, MATH));

    send(
            post("/api/v1/study-plans/batch"),
            """
            {"plans":[
              {"date":"2026-10-05","subjectId":"3","content":"수학","targetMinutes":60},
              {"date":"2026-10-05","content":"농구","targetMinutes":60,"kind":"play"}
            ]}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(2));
    verify(studyPlanService)
        .createAll(
            new StudyPlanBatchCreateRequest(
                List.of(
                    new StudyPlanCreateRequest(OCT_5, "3", "수학", 60, null, null),
                    new StudyPlanCreateRequest(OCT_5, null, "농구", 60, null, "play"))));
  }

  @Test
  @DisplayName("여러 개 추가에서 빈 목록은 400")
  void createAllEmpty() throws Exception {
    expectRejected(
        send(post("/api/v1/study-plans/batch"), "{\"plans\":[]}"), "추가할 계획을 하나 이상 보내 주세요");
  }

  @Test
  @DisplayName("여러 개 중 하나라도 틀리면 400 이고, Service 는 부르지 않는다 (하나도 저장 안 됨)")
  void createAllOneWrong() throws Exception {
    String wrong = VALID_PLAN.replace("수학", "");
    expectRejected(
        send(
            post("/api/v1/study-plans/batch"),
            "{\"plans\":[{" + VALID_PLAN + "},{" + wrong + "}]}"),
        "할 일을 입력해 주세요");
  }

  @Test
  @DisplayName("여러 개 추가는 50개까지")
  void createAllTooMany() throws Exception {
    String body =
        "{\"plans\":[" + String.join(",", Collections.nCopies(51, "{" + VALID_PLAN + "}")) + "]}";
    expectRejected(send(post("/api/v1/study-plans/batch"), body), "계획은 한 번에 50개까지 추가할 수 있어요");
  }

  // ---- 수정 ----

  @Test
  @DisplayName("수정은 200 이고, 보낸 값만 Service 로 넘긴다")
  void update() throws Exception {
    given(studyPlanService.update(eq(7L), any())).willReturn(MATH);

    send(patch("/api/v1/study-plans/7"), "{\"done\":true}").andExpect(status().isOk());
    verify(studyPlanService)
        .update(7L, new StudyPlanUpdateRequest(null, null, null, true, null, null));
  }

  @Test
  @DisplayName("시작 시각을 빈 글자로 보내면 검사를 통과한다 (시간 지우기)")
  void updateClearStartTime() throws Exception {
    given(studyPlanService.update(eq(7L), any())).willReturn(MATH);

    send(patch("/api/v1/study-plans/7"), "{\"startTime\":\"\"}").andExpect(status().isOk());
    verify(studyPlanService)
        .update(7L, new StudyPlanUpdateRequest(null, null, null, null, "", null));
  }

  static Stream<Arguments> wrongUpdateRequests() {
    return Stream.of(
        Arguments.of("{\"content\":\"   \"}", "할 일은 1자 이상 200자 이하로 적어 주세요"),
        Arguments.of("{\"targetMinutes\":0}", "목표 시간은 1분 이상이어야 해요"),
        Arguments.of("{\"targetMinutes\":601}", "목표 시간은 600분(10시간) 이하여야 해요"),
        Arguments.of("{\"startTime\":\"24:00\"}", "시작 시각은 19:00 처럼 보내 주세요"),
        Arguments.of("{\"subjectId\":\"x\"}", "과목 번호가 올바르지 않아요"),
        Arguments.of("{\"subjectId\":\"9999999999999999999\"}", "과목 번호가 올바르지 않아요"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("wrongUpdateRequests")
  @DisplayName("수정 요청이 틀리면 400 이고, Service 는 부르지 않는다")
  void updateRejected(String body, String message) throws Exception {
    expectRejected(send(patch("/api/v1/study-plans/7"), body), message);
  }

  @Test
  @DisplayName("남의 계획을 수정하면 Service 가 던진 404 가 그대로 나간다")
  void updateOthersPlan() throws Exception {
    given(studyPlanService.update(eq(8L), any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "계획을 찾을 수 없어요"));

    send(patch("/api/v1/study-plans/8"), "{\"done\":true}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("계획을 찾을 수 없어요"));
  }

  // ---- 삭제 ----

  @Test
  @DisplayName("삭제는 204 이고 본문이 없다")
  void deletePlan() throws Exception {
    mockMvc
        .perform(delete("/api/v1/study-plans/7"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(studyPlanService).delete(7L);
  }

  @Test
  @DisplayName("남의 계획을 지우면 404, 주소의 번호가 숫자가 아니면 400")
  void deleteErrors() throws Exception {
    willThrow(new ApiException(ErrorCode.NOT_FOUND, "계획을 찾을 수 없어요"))
        .given(studyPlanService)
        .delete(8L);

    mockMvc.perform(delete("/api/v1/study-plans/8")).andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/v1/study-plans/abc")).andExpect(status().isBadRequest());
  }
}
