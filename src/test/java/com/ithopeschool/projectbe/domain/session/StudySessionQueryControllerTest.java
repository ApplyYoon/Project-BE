package com.ithopeschool.projectbe.domain.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.session.dto.PlanActualResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudyTotalResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudyTotalResponse.SubjectTotalResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 공부 기록 "조회" 세 주소의 요청 모양 검사와 응답 모양을 확인합니다. (이슈 #14)
// 진짜 로직(StudySessionService)은 가짜로 바꿉니다. 하루의 기준·순공 계산은 Service·계산기 테스트가 확인합니다.
// FE 가 쓰는 이름(pausedMs, planId, isReached ...)이 글자 하나까지 같은지가 이 테스트의 핵심입니다.
@WebMvcTest(StudySessionController.class)
class StudySessionQueryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StudySessionService studySessionService;

  private static final String BAD_VALUE_MESSAGE = "보낸 값을 다시 확인해 주세요";

  private ResultActions getSessions(String from, String to) throws Exception {
    var request = get("/api/v1/study-sessions");
    if (from != null) {
      request.param("from", from);
    }
    if (to != null) {
      request.param("to", to);
    }
    return mockMvc.perform(request);
  }

  // ---- 기간별 목록 ----

  @Test
  @DisplayName("기간을 보내면 200 이고, 기록마다 FE StudySession 의 열 칸을 돌려준다")
  void sessionsInPeriod() throws Exception {
    given(studySessionService.getSessions(any(), any()))
        .willReturn(
            List.of(
                new StudySessionResponse(
                    "12",
                    "3",
                    1_790_000_000_000L,
                    1_790_003_000_000L,
                    2,
                    300_000L,
                    "7",
                    50,
                    "phone",
                    "study"),
                new StudySessionResponse(
                    "13",
                    "",
                    1_790_010_000_000L,
                    1_790_011_800_000L,
                    0,
                    0,
                    null,
                    null,
                    null,
                    "play")));

    getSessions("2026-10-01", "2026-10-07")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("12"))
        .andExpect(jsonPath("$[0].subjectId").value("3"))
        .andExpect(jsonPath("$[0].startedAt").value(1_790_000_000_000L))
        .andExpect(jsonPath("$[0].endedAt").value(1_790_003_000_000L))
        .andExpect(jsonPath("$[0].pauseCount").value(2))
        .andExpect(jsonPath("$[0].pausedMs").value(300_000))
        .andExpect(jsonPath("$[0].planId").value("7"))
        .andExpect(jsonPath("$[0].targetMinutes").value(50))
        .andExpect(jsonPath("$[0].stopReason").value("phone"))
        .andExpect(jsonPath("$[0].kind").value("study"))
        // 옛 열 칸 + 카드 기준으로 넓힌 여덟 칸(#224) = 열여덟 칸
        .andExpect(jsonPath("$[0].mode").value("normal"))
        .andExpect(jsonPath("$[0].endedEarly").value(false))
        .andExpect(jsonPath("$[0].cardId").isEmpty())
        .andExpect(jsonPath("$[0].length()").value(18))
        .andExpect(jsonPath("$[0].userId").doesNotExist())
        // 노는 기록: 과목은 빈 글자, 값이 없는 칸은 빼지 않고 null
        .andExpect(jsonPath("$[1].subjectId").value(""))
        .andExpect(jsonPath("$[1].planId").isEmpty())
        .andExpect(jsonPath("$[1].targetMinutes").isEmpty())
        .andExpect(jsonPath("$[1].stopReason").isEmpty())
        .andExpect(jsonPath("$[1].kind").value("play"))
        .andExpect(jsonPath("$[1].length()").value(18));

    verify(studySessionService).getSessions(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));
  }

  @Test
  @DisplayName("기록이 없는 기간은 404 가 아니라 빈 배열이다")
  void emptyPeriod() throws Exception {
    given(studySessionService.getSessions(any(), any())).willReturn(List.of());

    getSessions("2026-10-01", "2026-10-07")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // 요청 모양이 틀린 경우들. 이때는 Service 까지 가지 않고 Controller 에서 400 으로 끝납니다.
  static Stream<Arguments> wrongPeriods() {
    return Stream.of(
        Arguments.of("from 없음", null, "2026-10-07"),
        Arguments.of("to 없음", "2026-10-01", null),
        Arguments.of("둘 다 없음", null, null),
        Arguments.of("슬래시 모양", "2026/10/01", "2026-10-07"),
        Arguments.of("숫자만 붙여 씀", "20261001", "2026-10-07"),
        Arguments.of("없는 달", "2026-13-01", "2026-10-07"),
        Arguments.of("없는 날", "2026-10-01", "2026-02-30"),
        Arguments.of("글자", "어제", "오늘"),
        Arguments.of("빈 값", "", ""));
  }

  @ParameterizedTest(name = "{0} → 400")
  @MethodSource("wrongPeriods")
  @DisplayName("from · to 가 없거나 날짜 모양이 틀리면 400 이고, Service 는 부르지 않는다")
  void wrongPeriod(String name, String from, String to) throws Exception {
    getSessions(from, to)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(BAD_VALUE_MESSAGE));
    verifyNoInteractions(studySessionService);
  }

  @Test
  @DisplayName("Service 가 던진 400 (기간이 31일보다 김)이 그대로 나간다")
  void periodTooLong() throws Exception {
    given(studySessionService.getSessions(any(), any()))
        .willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "한 번에 31일까지만 조회할 수 있어요"));

    getSessions("2026-10-01", "2026-11-01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("한 번에 31일까지만 조회할 수 있어요"));
  }

  @Test
  @DisplayName("로그인하지 않았으면 Service 가 던진 401 이 그대로 나간다")
  void sessionsUnauthorized() throws Exception {
    given(studySessionService.getSessions(any(), any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    getSessions("2026-10-01", "2026-10-07")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  // ---- 오늘 순공시간 ----

  @Test
  @DisplayName("오늘 순공시간은 날짜·분·과목별 합계를 돌려주고, 과목 번호는 글자다")
  void todayTotal() throws Exception {
    given(studySessionService.getTodayTotal())
        .willReturn(
            new StudyTotalResponse(
                LocalDate.of(2026, 10, 5),
                100,
                List.of(new SubjectTotalResponse("3", 70), new SubjectTotalResponse("4", 30))));

    mockMvc
        .perform(get("/api/v1/study-sessions/today/total"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-05"))
        .andExpect(jsonPath("$.studyMinutes").value(100))
        .andExpect(jsonPath("$.subjects.length()").value(2))
        .andExpect(jsonPath("$.subjects[0].subjectId").value("3"))
        .andExpect(jsonPath("$.subjects[0].studyMinutes").value(70))
        .andExpect(jsonPath("$.subjects[0].length()").value(2))
        .andExpect(jsonPath("$.subjects[1].subjectId").value("4"))
        .andExpect(jsonPath("$.subjects[1].studyMinutes").value(30))
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  @DisplayName("오늘 공부한 과목이 없으면 0분이고 subjects 는 빠지지 않고 빈 배열이다")
  void todayTotalWithoutRecords() throws Exception {
    given(studySessionService.getTodayTotal())
        .willReturn(new StudyTotalResponse(LocalDate.of(2026, 10, 5), 0, List.of()));

    mockMvc
        .perform(get("/api/v1/study-sessions/today/total"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studyMinutes").value(0))
        .andExpect(jsonPath("$.subjects").isArray())
        .andExpect(jsonPath("$.subjects.length()").value(0));
  }

  @Test
  @DisplayName("오늘 순공시간도 로그인하지 않았으면 401 이다")
  void todayTotalUnauthorized() throws Exception {
    given(studySessionService.getTodayTotal())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/study-sessions/today/total"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  // ---- 계획별 실제 시간 ----

  @Test
  @DisplayName("계획별 실제 시간은 FE PlanProgress 와 같은 이름(isReached 포함)으로 돌려준다")
  void planActuals() throws Exception {
    given(studySessionService.getPlanActuals(any()))
        .willReturn(
            List.of(
                new PlanActualResponse("7", 32, 64, false, 18),
                new PlanActualResponse("8", 50, 100, true, 0)));

    mockMvc
        .perform(get("/api/v1/study-sessions/by-plan").param("date", "2026-10-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].planId").value("7"))
        .andExpect(jsonPath("$[0].actualMinutes").value(32))
        .andExpect(jsonPath("$[0].percent").value(64))
        .andExpect(jsonPath("$[0].isReached").value(false))
        .andExpect(jsonPath("$[0].remainingMinutes").value(18))
        .andExpect(jsonPath("$[0].length()").value(5))
        // 이름이 isReached 가 아니라 reached 로 나가면 FE 가 못 읽습니다
        .andExpect(jsonPath("$[0].reached").doesNotExist())
        .andExpect(jsonPath("$[1].planId").value("8"))
        .andExpect(jsonPath("$[1].isReached").value(true))
        .andExpect(jsonPath("$[1].remainingMinutes").value(0));

    verify(studySessionService).getPlanActuals(LocalDate.of(2026, 10, 5));
  }

  @Test
  @DisplayName("계획이 없는 날은 빈 배열이다")
  void noPlans() throws Exception {
    given(studySessionService.getPlanActuals(any())).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/study-sessions/by-plan").param("date", "2026-10-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  static Stream<Arguments> wrongDates() {
    return Stream.of(
        Arguments.of("date 없음", null),
        Arguments.of("슬래시 모양", "2026/10/05"),
        Arguments.of("없는 달", "2026-13-01"),
        Arguments.of("글자", "오늘"),
        Arguments.of("빈 값", ""));
  }

  @ParameterizedTest(name = "{0} → 400")
  @MethodSource("wrongDates")
  @DisplayName("계획별 실제 시간의 date 가 없거나 모양이 틀리면 400 이고, Service 는 부르지 않는다")
  void wrongDate(String name, String date) throws Exception {
    var request = get("/api/v1/study-sessions/by-plan");
    if (date != null) {
      request.param("date", date);
    }

    mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(BAD_VALUE_MESSAGE));
    verifyNoInteractions(studySessionService);
  }

  @Test
  @DisplayName("계획별 실제 시간도 로그인하지 않았으면 401 이다")
  void planActualsUnauthorized() throws Exception {
    given(studySessionService.getPlanActuals(any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/study-sessions/by-plan").param("date", "2026-10-05"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }
}
