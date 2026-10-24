package com.ithopeschool.projectbe.domain.answercheck;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호 · JSON 모양을 확인합니다. 진짜 로직(AnswerCheckService)은 가짜입니다.
@WebMvcTest(AnswerCheckController.class)
class AnswerCheckControllerTest {

  private static final LocalDate DATE = LocalDate.parse("2026-10-24");
  private static final String IMAGE = "data:image/jpeg;base64,AQIDBA==";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AnswerCheckService answerCheckService;

  // 계약서(FE 와 맞춘 모양)의 예시와 같은 응답
  private static AnswerCheckResponse sample() {
    return new AnswerCheckResponse(
        false,
        true,
        null,
        "12",
        DATE,
        AnswerCheckMode.UNMARKED,
        "수학",
        new AnswerCheckResponse.Counts(1, 0, 0),
        List.of(
            new AnswerCheckResponse.Item(
                "31", "1", "x = 5", "x = 5", null, GradeResult.CORRECT, null, false)),
        Instant.parse("2026-10-24T11:02:00Z"));
  }

  private ResultActions create(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/answer-checks").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions update(String path, String body) throws Exception {
    return mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("사진을 올리면 200 과 계약서 모양의 채점 결과를 돌려준다")
  void creates() throws Exception {
    given(answerCheckService.create(any())).willReturn(sample());

    create("{\"mode\":\"UNMARKED\",\"image\":\"" + IMAGE + "\",\"date\":\"2026-10-24\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showHelp").value(false))
        .andExpect(jsonPath("$.graded").value(true))
        .andExpect(jsonPath("$.reason").isEmpty())
        .andExpect(jsonPath("$.id").value("12"))
        .andExpect(jsonPath("$.date").value("2026-10-24"))
        .andExpect(jsonPath("$.mode").value("UNMARKED"))
        .andExpect(jsonPath("$.subjectText").value("수학"))
        .andExpect(jsonPath("$.counts.correct").value(1))
        .andExpect(jsonPath("$.counts.wrong").value(0))
        .andExpect(jsonPath("$.counts.unsure").value(0))
        .andExpect(jsonPath("$.items[0].id").value("31"))
        .andExpect(jsonPath("$.items[0].number").value("1"))
        .andExpect(jsonPath("$.items[0].myAnswer").value("x = 5"))
        .andExpect(jsonPath("$.items[0].aiAnswer").value("x = 5"))
        .andExpect(jsonPath("$.items[0].markRead").isEmpty())
        .andExpect(jsonPath("$.items[0].result").value("CORRECT"))
        .andExpect(jsonPath("$.items[0].correctAnswer").isEmpty())
        .andExpect(jsonPath("$.items[0].editedByUser").value(false))
        .andExpect(jsonPath("$.createdAt").value("2026-10-24T11:02:00Z"));
  }

  @Test
  @DisplayName("채점하지 못하면 에러가 아니라 200 에 graded:false 와 이유를 돌려준다")
  void notGradedIsOk() throws Exception {
    given(answerCheckService.create(any()))
        .willReturn(
            AnswerCheckResponse.notGraded(
                AnswerCheckResponse.UNAVAILABLE, DATE, AnswerCheckMode.MARKED));

    create("{\"mode\":\"MARKED\",\"image\":\"" + IMAGE + "\",\"date\":\"2026-10-24\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graded").value(false))
        .andExpect(jsonPath("$.reason").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.id").isEmpty())
        .andExpect(jsonPath("$.counts").isEmpty())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  @DisplayName("모드 · 사진 · 날짜가 비었거나 모드가 모르는 값이면 400 이고 Service 를 부르지 않는다")
  void rejectsBadCreate() throws Exception {
    create("{\"image\":\"" + IMAGE + "\",\"date\":\"2026-10-24\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    create("{\"mode\":\"MARKED\",\"image\":\"  \",\"date\":\"2026-10-24\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    create("{\"mode\":\"MARKED\",\"image\":\"" + IMAGE + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    create("{\"mode\":\"HALF\",\"image\":\"" + IMAGE + "\",\"date\":\"2026-10-24\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(answerCheckService);
  }

  @Test
  @DisplayName("로그인하지 않으면 401 이다 (Service 의 getCurrentUserId 가 던짐)")
  void unauthorized() throws Exception {
    given(answerCheckService.getChecks(DATE))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"));

    mockMvc
        .perform(get("/api/v1/answer-checks").param("date", "2026-10-24"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("그날 목록은 200 과 배열, 날짜가 없거나 모양이 틀리면 400")
  void lists() throws Exception {
    given(answerCheckService.getChecks(DATE)).willReturn(List.of(sample()));

    mockMvc
        .perform(get("/api/v1/answer-checks").param("date", "2026-10-24"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("12"));

    mockMvc.perform(get("/api/v1/answer-checks")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/answer-checks").param("date", "10월 24일"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("판정을 고치면 200 과 고친 기록 전체, 없는 번호면 404")
  void updatesItem() throws Exception {
    given(answerCheckService.updateItem(eq(12L), eq(31L), any())).willReturn(sample());
    given(answerCheckService.updateItem(eq(12L), eq(99L), any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없어요"));

    update(
            "/api/v1/answer-checks/12/items/31",
            "{\"result\":\"WRONG\",\"correctAnswer\":\"x = 7\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("12"));

    update("/api/v1/answer-checks/12/items/99", "{\"result\":\"WRONG\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("문제를 찾을 수 없어요"));
  }

  @Test
  @DisplayName("판정이 비었거나 모르는 값이거나 정답이 100자를 넘으면 400")
  void rejectsBadUpdate() throws Exception {
    update("/api/v1/answer-checks/12/items/31", "{\"correctAnswer\":\"x = 7\"}")
        .andExpect(status().isBadRequest());
    update("/api/v1/answer-checks/12/items/31", "{\"result\":\"MAYBE\"}")
        .andExpect(status().isBadRequest());
    update(
            "/api/v1/answer-checks/12/items/31",
            "{\"result\":\"WRONG\",\"correctAnswer\":\"" + "가".repeat(101) + "\"}")
        .andExpect(status().isBadRequest());
    update("/api/v1/answer-checks/abc/items/31", "{\"result\":\"WRONG\"}")
        .andExpect(status().isBadRequest());
    verifyNoInteractions(answerCheckService);
  }
}
