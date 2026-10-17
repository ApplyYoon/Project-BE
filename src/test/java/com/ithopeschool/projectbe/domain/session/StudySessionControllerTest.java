package com.ithopeschool.projectbe.domain.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.session.dto.SessionEndReportRequest;
import com.ithopeschool.projectbe.domain.session.dto.SessionMemoRequest;
import com.ithopeschool.projectbe.domain.session.dto.SessionPauseRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
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

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다.
// 진짜 로직(StudySessionService)은 가짜로 바꿉니다. 저장 규칙은 StudySessionServiceTest 가 확인합니다.
@WebMvcTest(StudySessionController.class)
class StudySessionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StudySessionService studySessionService;

  private static final long START = 1_790_000_000_000L;
  private static final long END = 1_790_003_000_000L;

  // 꼭 필요한 칸만 있는 요청. 바꾸고 싶은 칸만 뒤에 덧붙여 씁니다.
  private static final String VALID =
      "\"subjectId\":\"3\",\"startedAt\":" + START + ",\"endedAt\":" + END + ",\"pauseCount\":2";

  private ResultActions send(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/study-sessions").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("저장하면 201 이고, FE StudySession 의 열여덟 칸(옛 열 칸 + 카드 칸 여덟)을 돌려준다")
  void create() throws Exception {
    given(studySessionService.create(any()))
        .willReturn(
            new StudySessionResponse(
                "12", "3", START, END, 2, 300_000L, "7", 50, "tooMuch", "study"));

    send("{"
            + VALID
            + ",\"pausedMs\":300000,\"planId\":\"7\",\"targetMinutes\":50,"
            + "\"stopReason\":\"tooMuch\",\"kind\":\"study\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("12"))
        .andExpect(jsonPath("$.subjectId").value("3"))
        .andExpect(jsonPath("$.startedAt").value(START))
        .andExpect(jsonPath("$.endedAt").value(END))
        .andExpect(jsonPath("$.pauseCount").value(2))
        .andExpect(jsonPath("$.pausedMs").value(300_000))
        .andExpect(jsonPath("$.planId").value("7"))
        .andExpect(jsonPath("$.targetMinutes").value(50))
        .andExpect(jsonPath("$.stopReason").value("tooMuch"))
        .andExpect(jsonPath("$.kind").value("study"))
        .andExpect(jsonPath("$.length()").value(18))
        .andExpect(jsonPath("$.userId").doesNotExist());
  }

  @Test
  @DisplayName("선택 칸이 없는 기록은 그 칸을 빼지 않고 null 로 보낸다")
  void nullOptionalValues() throws Exception {
    given(studySessionService.create(any()))
        .willReturn(new StudySessionResponse("12", "", START, END, 0, 0, null, null, null, "play"));

    send("{" + VALID + "}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.subjectId").value(""))
        .andExpect(jsonPath("$.planId").isEmpty())
        .andExpect(jsonPath("$.targetMinutes").isEmpty())
        .andExpect(jsonPath("$.stopReason").isEmpty())
        // 카드 칸도 빠지지 않습니다. 일시정지가 0번이면 pauses 는 빈 배열입니다.
        .andExpect(jsonPath("$.cardId").isEmpty())
        .andExpect(jsonPath("$.mode").value("normal"))
        .andExpect(jsonPath("$.pauses.length()").value(0))
        .andExpect(jsonPath("$.endedEarly").value(false))
        .andExpect(jsonPath("$.endReport").isEmpty())
        .andExpect(jsonPath("$.length()").value(18));
  }

  // 요청 모양이 틀린 경우들. 이때는 Service 까지 가지 않고 Controller 에서 400 으로 끝납니다.
  static Stream<Arguments> wrongRequests() {
    String times = "\"startedAt\":" + START + ",\"endedAt\":" + END;
    return Stream.of(
        Arguments.of(
            "시작 시각 없음",
            "{\"subjectId\":\"3\",\"endedAt\":" + END + ",\"pauseCount\":0}",
            "시작 시각을 보내 주세요"),
        Arguments.of(
            "끝 시각 없음",
            "{\"subjectId\":\"3\",\"startedAt\":" + START + ",\"pauseCount\":0}",
            "끝 시각을 보내 주세요"),
        Arguments.of("일시정지 횟수 없음", "{\"subjectId\":\"3\"," + times + "}", "일시정지 횟수를 보내 주세요"),
        Arguments.of(
            "일시정지 횟수가 음수",
            "{\"subjectId\":\"3\"," + times + ",\"pauseCount\":-1}",
            "일시정지 횟수는 0 이상이어야 해요"),
        Arguments.of("일시정지 시간이 음수", "{" + VALID + ",\"pausedMs\":-1}", "일시정지 시간은 0 이상이어야 해요"),
        Arguments.of(
            "과목 번호가 숫자가 아님",
            "{\"subjectId\":\"abc\"," + times + ",\"pauseCount\":0}",
            "과목 번호가 올바르지 않아요"),
        // 19자리 숫자는 서버의 숫자(Long)에 안 들어갈 수 있어서 400 으로 막습니다. (500 이 나면 안 됨)
        Arguments.of(
            "과목 번호가 19자리",
            "{\"subjectId\":\"9999999999999999999\"," + times + ",\"pauseCount\":0}",
            "과목 번호가 올바르지 않아요"),
        Arguments.of(
            "계획 번호가 19자리", "{" + VALID + ",\"planId\":\"9999999999999999999\"}", "계획 번호가 올바르지 않아요"),
        Arguments.of("계획 번호가 숫자가 아님", "{" + VALID + ",\"planId\":\"x\"}", "계획 번호가 올바르지 않아요"),
        Arguments.of("목표 시간이 0분", "{" + VALID + ",\"targetMinutes\":0}", "목표 시간은 1분 이상이어야 해요"),
        Arguments.of(
            "목표 시간이 601분", "{" + VALID + ",\"targetMinutes\":601}", "목표 시간은 600분(10시간) 이하여야 해요"),
        // ---- 카드 칸 (이슈 #224) ----
        Arguments.of("카드 번호가 숫자가 아님", "{" + VALID + ",\"cardId\":\"c1\"}", "카드 번호가 올바르지 않아요"),
        Arguments.of(
            "일시정지 이유 없음",
            "{" + VALID + ",\"pauses\":[{\"startedAt\":" + START + ",\"endedAt\":" + END + "}]}",
            "일시정지 이유를 보내 주세요"),
        Arguments.of(
            "일시정지 메모가 101자",
            "{"
                + VALID
                + ",\"pauses\":[{\"startedAt\":"
                + START
                + ",\"endedAt\":"
                + END
                + ",\"tag\":\"other\",\"note\":\""
                + "가".repeat(101)
                + "\"}]}",
            "일시정지 메모는 100자 이하로 적어 주세요"),
        Arguments.of(
            "메모가 비었음",
            "{" + VALID + ",\"memos\":[{\"at\":" + START + ",\"text\":\" \"}]}",
            "메모를 입력해 주세요"),
        Arguments.of(
            "증빙 링크가 301자",
            "{" + VALID + ",\"endReport\":{\"proof\":{\"link\":\"" + "a".repeat(301) + "\"}}}",
            "증빙 링크는 300자 이하로 보내 주세요"),
        Arguments.of("초과 휴식이 음수", "{" + VALID + ",\"overRestMs\":-1}", "초과 휴식 시간은 0 이상이어야 해요"));
  }

  @Test
  @DisplayName("카드 칸(일시정지 · 종료 팝업 · 메모)을 보내면 모양을 검사한 뒤 그대로 Service 로 넘긴다")
  void createWithCardFields() throws Exception {
    given(studySessionService.create(any()))
        .willReturn(new StudySessionResponse("12", "", START, END, 0, 0, null, 60, null, "study"));

    send("{\"subjectId\":\"\",\"startedAt\":"
            + START
            + ",\"endedAt\":"
            + END
            + ",\"pauseCount\":1,\"targetMinutes\":60,\"kind\":\"study\","
            + "\"cardId\":\"7\",\"mode\":\"offline\",\"endedEarly\":true,"
            + "\"pauses\":[{\"startedAt\":"
            + (START + 60_000)
            + ",\"endedAt\":"
            + (START + 120_000)
            + ",\"tag\":\"toilet\"}],"
            + "\"endReport\":{\"checked\":{\"i1\":"
            + (START + 300_000)
            + "},\"withWhat\":\"music\",\"proof\":{\"summary\":\" 42쪽 \"},\"goodReasons\":[\"focus\"]},"
            + "\"memos\":[{\"at\":"
            + (START + 200_000)
            + ",\"text\":\" 문자 보내기 \"}]}")
        .andExpect(status().isCreated());

    verify(studySessionService)
        .create(
            new StudySessionCreateRequest(
                "",
                START,
                END,
                1,
                null,
                null,
                60,
                null,
                "study",
                "7",
                "offline",
                List.of(new SessionPauseRequest(START + 60_000, START + 120_000, "toilet", null)),
                true,
                new SessionEndReportRequest(
                    Map.of("i1", START + 300_000),
                    null,
                    "music",
                    new SessionEndReportRequest.Proof(null, "42쪽"),
                    List.of("focus")),
                List.of(new SessionMemoRequest(START + 200_000, "문자 보내기")),
                null,
                null));
  }

  @ParameterizedTest(name = "{0} → 400")
  @MethodSource("wrongRequests")
  @DisplayName("요청 모양이 틀리면 400 이고, Service 는 부르지 않는다")
  void wrongRequest(String name, String body, String message) throws Exception {
    send(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(studySessionService);
  }

  @Test
  @DisplayName("시각을 숫자가 아닌 글자로 보내면 400")
  void timeAsText() throws Exception {
    send("{\"subjectId\":\"3\",\"startedAt\":\"오늘\",\"endedAt\":" + END + ",\"pauseCount\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(studySessionService);
  }

  @Test
  @DisplayName("Service 가 던진 409 (겹치는 기록)가 그대로 나간다")
  void conflict() throws Exception {
    given(studySessionService.create(any()))
        .willThrow(new ApiException(ErrorCode.CONFLICT, "이미 저장된 기록과 시간이 겹쳐요"));

    send("{" + VALID + "}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"))
        .andExpect(jsonPath("$.error.message").value("이미 저장된 기록과 시간이 겹쳐요"));
  }

  @Test
  @DisplayName("로그인하지 않았으면 Service 가 던진 401 이 그대로 나간다")
  void unauthorized() throws Exception {
    given(studySessionService.create(any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    send("{" + VALID + "}")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("모두 지우기는 204(본문 없음)이고, 로그인하지 않았으면 401 이다")
  void deletesAll() throws Exception {
    mockMvc.perform(delete("/api/v1/study-sessions")).andExpect(status().isNoContent());
    verify(studySessionService).deleteAll();

    willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"))
        .given(studySessionService)
        .deleteAll();
    mockMvc.perform(delete("/api/v1/study-sessions")).andExpect(status().isUnauthorized());
  }
}
