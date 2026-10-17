package com.ithopeschool.projectbe.domain.exam;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.exam.dto.ExamActualScoreRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamCreateRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamExpectedScoreRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamResponse;
import com.ithopeschool.projectbe.domain.exam.dto.ExamResultRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamSubjectScoreResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(ExamService)은 가짜로 바꿉니다.
@WebMvcTest(ExamController.class)
class ExamControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ExamService examService;

  private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);

  // 시험 전: 예상 점수만 있습니다.
  private static final ExamResponse BEFORE_EXAM =
      new ExamResponse(
          "7",
          "mock",
          "10월 모의고사",
          OCT_20,
          List.of(
              new ExamSubjectScoreResponse("3", 85, null),
              new ExamSubjectScoreResponse("4", 70, null)));

  // 시험 등록 요청 한 개의 JSON. 바꾸고 싶은 칸을 replace 로 바꿔 씁니다.
  private static final String VALID_EXAM =
      "{\"type\":\"mock\",\"name\":\"10월 모의고사\",\"date\":\"2026-10-20\","
          + "\"subjectScores\":[{\"subjectId\":\"3\",\"expectedScore\":85}]}";

  // 실제 점수 입력 요청 한 개의 JSON.
  private static final String VALID_RESULT =
      "{\"subjectScores\":[{\"subjectId\":\"3\",\"actualScore\":80}]}";

  private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(examService);
  }

  // ---- 목록 ----

  @Test
  @DisplayName("목록은 200 이고, 시험마다 FE Exam 의 다섯 칸이 있다")
  void getExams() throws Exception {
    given(examService.getExams()).willReturn(List.of(BEFORE_EXAM));

    mockMvc
        .perform(get("/api/v1/exams"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("7"))
        .andExpect(jsonPath("$[0].type").value("mock"))
        .andExpect(jsonPath("$[0].name").value("10월 모의고사"))
        .andExpect(jsonPath("$[0].date").value("2026-10-20"))
        .andExpect(jsonPath("$[0].subjectScores.length()").value(2))
        .andExpect(jsonPath("$[0].subjectScores[0].subjectId").value("3"))
        .andExpect(jsonPath("$[0].subjectScores[0].expectedScore").value(85))
        // 실제 점수는 시험 후에 적어서, 아직이면 빼지 않고 null 로 보냅니다. (docs/API.md 4장)
        .andExpect(jsonPath("$[0].subjectScores[0].actualScore").isEmpty())
        .andExpect(jsonPath("$[0].subjectScores[0].length()").value(3))
        .andExpect(jsonPath("$[0].length()").value(5))
        .andExpect(jsonPath("$[0].userId").doesNotExist());
  }

  @Test
  @DisplayName("시험이 없으면 404 가 아니라 빈 배열 []")
  void emptyList() throws Exception {
    given(examService.getExams()).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/exams"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));
  }

  @Test
  @DisplayName("로그인하지 않았으면 Service 가 던진 401 이 그대로 나간다")
  void unauthorized() throws Exception {
    given(examService.getExams()).willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/exams"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("로그인이 필요해요"));
  }

  // ---- 등록 ----

  @Test
  @DisplayName("등록은 201 이고 만들어진 시험을 돌려준다")
  void create() throws Exception {
    given(examService.create(any())).willReturn(BEFORE_EXAM);

    send(
            post("/api/v1/exams"),
            """
            {"type":"mock","name":"10월 모의고사","date":"2026-10-20",
             "subjectScores":[{"subjectId":"3","expectedScore":85},
                              {"subjectId":"4","expectedScore":70}]}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("7"))
        .andExpect(jsonPath("$.type").value("mock"))
        .andExpect(jsonPath("$.subjectScores.length()").value(2));
    verify(examService)
        .create(
            new ExamCreateRequest(
                "mock",
                "10월 모의고사",
                OCT_20,
                List.of(
                    new ExamExpectedScoreRequest("3", BigDecimal.valueOf(85)),
                    new ExamExpectedScoreRequest("4", BigDecimal.valueOf(70)))));
  }

  @Test
  @DisplayName("점수 0 과 100 은 된다 (범위의 양 끝)")
  void createAcceptsScoreEdges() throws Exception {
    given(examService.create(any())).willReturn(BEFORE_EXAM);

    send(post("/api/v1/exams"), VALID_EXAM.replace("85", "0")).andExpect(status().isCreated());
    send(post("/api/v1/exams"), VALID_EXAM.replace("85", "100")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("시험 이름의 앞뒤 공백은 지우고 넘긴다")
  void createTrimsName() throws Exception {
    given(examService.create(any())).willReturn(BEFORE_EXAM);

    send(post("/api/v1/exams"), VALID_EXAM.replace("10월 모의고사", "  10월 모의고사  "))
        .andExpect(status().isCreated());
    verify(examService)
        .create(
            new ExamCreateRequest(
                "mock",
                "10월 모의고사",
                OCT_20,
                List.of(new ExamExpectedScoreRequest("3", BigDecimal.valueOf(85)))));
  }

  // { 보낼 JSON, 돌아와야 할 문장 }
  static Stream<Arguments> wrongCreateRequests() {
    String fifty =
        IntStream.range(0, 51)
            .mapToObj(i -> "{\"subjectId\":\"" + (i + 1) + "\",\"expectedScore\":80}")
            .reduce((a, b) -> a + "," + b)
            .orElseThrow();
    return Stream.of(
        Arguments.of(VALID_EXAM.replace("\"type\":\"mock\",", ""), "시험 종류를 골라 주세요"),
        Arguments.of(VALID_EXAM.replace("\"mock\"", "\"  \""), "시험 종류를 골라 주세요"),
        Arguments.of(VALID_EXAM.replace("\"name\":\"10월 모의고사\",", ""), "시험 이름을 입력해 주세요"),
        Arguments.of(VALID_EXAM.replace("10월 모의고사", "   "), "시험 이름을 입력해 주세요"),
        Arguments.of(VALID_EXAM.replace("10월 모의고사", "가".repeat(51)), "시험 이름은 50자 이하로 적어 주세요"),
        Arguments.of(VALID_EXAM.replace("\"date\":\"2026-10-20\",", ""), "시험 날짜를 보내 주세요"),
        Arguments.of(VALID_EXAM.replace("2026-10-20", "2026/10/20"), "보낸 값을 다시 확인해 주세요"),
        Arguments.of(
            VALID_EXAM.replaceAll("\"subjectScores\":.*\\}\\]", "\"subjectScores\":[]"),
            "과목 하나 이상에 예상 점수를 적어 주세요"),
        Arguments.of(
            VALID_EXAM.replaceAll(",\"subjectScores\":.*\\}\\]", ""), "과목 하나 이상에 예상 점수를 적어 주세요"),
        Arguments.of(
            VALID_EXAM.replaceAll(
                "\"subjectScores\":.*\\}\\]", "\"subjectScores\":[" + fifty + "]"),
            "과목은 한 시험에 50개까지 적을 수 있어요"),
        Arguments.of(VALID_EXAM.replace("\"subjectId\":\"3\",", ""), "과목을 골라 주세요"),
        Arguments.of(VALID_EXAM.replace("\"3\"", "\"abc\""), "과목 번호가 올바르지 않아요"),
        Arguments.of(VALID_EXAM.replace("\"3\"", "\"\""), "과목 번호가 올바르지 않아요"),
        // 19자리는 서버의 숫자(Long)에 안 들어갈 수 있어서 400 으로 막습니다. (500 이 나면 안 됨)
        Arguments.of(VALID_EXAM.replace("\"3\"", "\"9999999999999999999\""), "과목 번호가 올바르지 않아요"),
        Arguments.of(VALID_EXAM.replace(",\"expectedScore\":85", ""), "예상 점수를 보내 주세요"),
        Arguments.of(VALID_EXAM.replace("85", "101"), "점수는 0~100 사이의 정수로 보내 주세요"),
        Arguments.of(VALID_EXAM.replace("85", "-1"), "점수는 0~100 사이의 정수로 보내 주세요"),
        // 정수가 아닌 점수는 85 로 잘라서 저장하지 않고 400 으로 막습니다. (직접 불러 보고 찾은 문제)
        Arguments.of(VALID_EXAM.replace("85", "85.5"), "점수는 0~100 사이의 정수로 보내 주세요"),
        Arguments.of(VALID_EXAM.replace("85", "0.5"), "점수는 0~100 사이의 정수로 보내 주세요"),
        Arguments.of(VALID_EXAM.replace("85", "100.5"), "점수는 0~100 사이의 정수로 보내 주세요"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("wrongCreateRequests")
  @DisplayName("등록 요청이 틀리면 400 이고, Service 는 부르지 않는다")
  void createRejected(String body, String message) throws Exception {
    expectRejected(send(post("/api/v1/exams"), body), message);
  }

  @Test
  @DisplayName("과목이 중복이거나 종류가 틀리면 Service 가 던진 400 이 그대로 나간다")
  void createServiceRejects() throws Exception {
    given(examService.create(any()))
        .willThrow(
            new ApiException(
                ErrorCode.VALIDATION_ERROR, "시험 종류는 mock, school, quiz, etc 중에서 골라 주세요"));

    send(post("/api/v1/exams"), VALID_EXAM.replace("\"mock\"", "\"midterm\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("시험 종류는 mock, school, quiz, etc 중에서 골라 주세요"));
  }

  @Test
  @DisplayName("내 과목이 아니면 Service 가 던진 404 가 그대로 나간다")
  void createWithOthersSubject() throws Exception {
    given(examService.create(any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요"));

    send(post("/api/v1/exams"), VALID_EXAM)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("과목을 찾을 수 없어요"));
  }

  // ---- 실제 점수 입력 ----

  @Test
  @DisplayName("실제 점수 입력은 200 이고 바뀐 시험을 돌려준다")
  void enterActualScores() throws Exception {
    given(examService.enterActualScores(eq(7L), any()))
        .willReturn(
            new ExamResponse(
                "7",
                "mock",
                "10월 모의고사",
                OCT_20,
                List.of(new ExamSubjectScoreResponse("3", 85, 80))));

    send(patch("/api/v1/exams/7"), VALID_RESULT)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("7"))
        .andExpect(jsonPath("$.subjectScores[0].expectedScore").value(85))
        .andExpect(jsonPath("$.subjectScores[0].actualScore").value(80));
    verify(examService)
        .enterActualScores(
            7L,
            new ExamResultRequest(
                List.of(new ExamActualScoreRequest("3", BigDecimal.valueOf(80)))));
  }

  // { 보낼 JSON, 돌아와야 할 문장 }
  static Stream<Arguments> wrongResultRequests() {
    return Stream.of(
        Arguments.of("{}", "실제 점수를 하나 이상 보내 주세요"),
        Arguments.of("{\"subjectScores\":[]}", "실제 점수를 하나 이상 보내 주세요"),
        Arguments.of(VALID_RESULT.replace("\"subjectId\":\"3\",", ""), "과목을 골라 주세요"),
        Arguments.of(VALID_RESULT.replace("\"3\"", "\"abc\""), "과목 번호가 올바르지 않아요"),
        Arguments.of(VALID_RESULT.replace("\"3\"", "\"9999999999999999999\""), "과목 번호가 올바르지 않아요"),
        Arguments.of(VALID_RESULT.replace(",\"actualScore\":80", ""), "실제 점수를 보내 주세요"),
        Arguments.of(VALID_RESULT.replace("80", "101"), "점수는 0~100 사이의 정수로 보내 주세요"),
        Arguments.of(VALID_RESULT.replace("80", "-1"), "점수는 0~100 사이의 정수로 보내 주세요"),
        // 정수가 아닌 점수는 80 으로 잘라서 저장하지 않고 400 으로 막습니다.
        Arguments.of(VALID_RESULT.replace("80", "80.5"), "점수는 0~100 사이의 정수로 보내 주세요"),
        Arguments.of(VALID_RESULT.replace("80", "0.5"), "점수는 0~100 사이의 정수로 보내 주세요"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("wrongResultRequests")
  @DisplayName("실제 점수 요청이 틀리면 400 이고, Service 는 부르지 않는다")
  void enterActualScoresRejected(String body, String message) throws Exception {
    expectRejected(send(patch("/api/v1/exams/7"), body), message);
  }

  @Test
  @DisplayName("주소의 시험 번호가 숫자가 아니면 400 이고, Service 는 부르지 않는다")
  void wrongExamId() throws Exception {
    send(patch("/api/v1/exams/abc"), VALID_RESULT)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(examService);
  }

  @Test
  @DisplayName("없는 시험이거나 남의 시험이면 Service 가 던진 404 가 그대로 나간다")
  void enterActualScoresNotFound() throws Exception {
    given(examService.enterActualScores(eq(99L), any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "시험을 찾을 수 없어요"));

    send(patch("/api/v1/exams/99"), VALID_RESULT)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("시험을 찾을 수 없어요"));
  }

  @Test
  @DisplayName("시험 날짜 전이면 Service 가 던진 400 이 그대로 나간다")
  void enterActualScoresTooEarly() throws Exception {
    given(examService.enterActualScores(eq(7L), any()))
        .willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "시험 날짜가 지난 뒤에 실제 점수를 넣을 수 있어요"));

    send(patch("/api/v1/exams/7"), VALID_RESULT)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("시험 날짜가 지난 뒤에 실제 점수를 넣을 수 있어요"));
  }

  @Test
  @DisplayName("지원하지 않는 요청 방법(DELETE)은 405")
  void methodNotAllowed() throws Exception {
    mockMvc
        .perform(delete("/api/v1/exams/7"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    verifyNoInteractions(examService);
  }

  @Test
  @DisplayName("과목 점수가 하나도 없는 시험도 subjectScores 는 빈 배열 []")
  void examWithoutScoresHasEmptyArray() throws Exception {
    given(examService.getExams())
        .willReturn(
            List.of(new ExamResponse("8", "etc", "기타 시험", OCT_20, Collections.emptyList())));

    mockMvc
        .perform(get("/api/v1/exams"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].subjectScores").isArray())
        .andExpect(jsonPath("$[0].subjectScores.length()").value(0));
  }
}
