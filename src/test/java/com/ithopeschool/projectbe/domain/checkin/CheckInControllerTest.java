package com.ithopeschool.projectbe.domain.checkin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.checkin.dto.CheckInQuestionsResponse;
import com.ithopeschool.projectbe.domain.checkin.dto.CheckInResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
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

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(CheckInService)은 가짜입니다.
@WebMvcTest(CheckInController.class)
class CheckInControllerTest {

  private static final LocalDate DATE = LocalDate.parse("2026-10-17");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CheckInService checkInService;
  @MockitoBean private CheckInQuestionService checkInQuestionService;

  @Test
  @DisplayName("오늘 물어볼 질문은 200 과 기본 · 자세히로 나눈 질문 목록이다")
  void getsTodayQuestions() throws Exception {
    given(checkInQuestionService.getTodayQuestions())
        .willReturn(
            new CheckInQuestionsResponse(
                DATE, List.of("mood"), List.of("condition", "expectedMinutes", "missReason")));

    mockMvc
        .perform(get("/api/v1/checkins/today/questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-17"))
        .andExpect(jsonPath("$.basic[0]").value("mood"))
        .andExpect(jsonPath("$.detail[2]").value("missReason"));
  }

  private ResultActions save(String date, String body) throws Exception {
    return mockMvc.perform(
        put("/api/v1/checkins/" + date).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    save("2026-10-17", body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(checkInService);
  }

  @Test
  @DisplayName("보내면 200 과 저장된 체크인을 돌려준다. 이름은 FE 의 CheckIn 과 같다")
  void saves() throws Exception {
    given(checkInService.save(eq(DATE), any()))
        .willReturn(
            new CheckInResponse(
                "3", DATE, "manual", 4, 3, "etc", "동아리 발표", 120, "폰을 보고 싶으면", "물 한 잔 마신다", true));

    save(
            "2026-10-17",
            "{\"source\":\"manual\",\"condition\":4,\"mood\":3,\"missReason\":\"etc\","
                + "\"missReasonNote\":\"동아리 발표\",\"expectedMinutes\":120,"
                + "\"promiseIf\":\"폰을 보고 싶으면\",\"promiseThen\":\"물 한 잔 마신다\","
                + "\"suggestionKept\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("3"))
        .andExpect(jsonPath("$.date").value("2026-10-17"))
        .andExpect(jsonPath("$.source").value("manual"))
        .andExpect(jsonPath("$.missReason").value("etc"))
        .andExpect(jsonPath("$.promiseThen").value("물 한 잔 마신다"))
        .andExpect(jsonPath("$.suggestionKept").value(true));
  }

  @Test
  @DisplayName("source 만 보내도 200 이다 (다른 칸은 다 비어도 된다)")
  void savesWithOnlySource() throws Exception {
    given(checkInService.save(eq(DATE), any()))
        .willReturn(
            new CheckInResponse(
                "3", DATE, "quick", null, null, null, null, null, null, null, null));

    save("2026-10-17", "{\"source\":\"quick\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mood").doesNotExist());
  }

  @Test
  @DisplayName("source 가 없거나, 숫자가 범위 밖이거나, 글이 너무 길면 400 이고 서비스를 부르지 않는다")
  void rejectsBadRequests() throws Exception {
    expectRejected("{\"mood\":3}", "어디서 한 체크인인지(source) 보내 주세요");
    expectRejected("{\"source\":\"manual\",\"condition\":0}", "컨디션은 1 ~ 5 중에서 골라 주세요");
    expectRejected("{\"source\":\"manual\",\"mood\":6}", "기분은 1 ~ 5 중에서 골라 주세요");
    expectRejected("{\"source\":\"manual\",\"expectedMinutes\":-1}", "예상 순공시간은 0분 이상이어야 해요");
    expectRejected(
        "{\"source\":\"manual\",\"expectedMinutes\":1441}", "예상 순공시간은 1440분(24시간) 이하여야 해요");
    expectRejected(
        "{\"source\":\"manual\",\"missReason\":\"etc\",\"missReasonNote\":\""
            + "가".repeat(51)
            + "\"}",
        "기타 이유는 50자까지 적을 수 있어요");
    expectRejected(
        "{\"source\":\"manual\",\"promiseIf\":\"" + "가".repeat(101) + "\",\"promiseThen\":\"한다\"}",
        "약속은 100자까지 적을 수 있어요");
  }

  @Test
  @DisplayName("주소의 날짜 모양이 틀리면 400 이다")
  void rejectsBadDate() throws Exception {
    save("2026-13-01", "{\"source\":\"manual\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(checkInService);
  }

  @Test
  @DisplayName("기간 목록은 200 과 배열이다. from · to 를 빼면 400 이다")
  void getsList() throws Exception {
    given(checkInService.getCheckIns(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07")))
        .willReturn(
            List.of(
                new CheckInResponse("3", DATE, "quick", 3, 3, null, null, 90, null, null, null)));

    mockMvc
        .perform(get("/api/v1/checkins?from=2026-10-01&to=2026-10-07"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].source").value("quick"))
        .andExpect(jsonPath("$[0].expectedMinutes").value(90));

    mockMvc
        .perform(get("/api/v1/checkins?from=2026-10-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("서비스가 401 을 던지면 그대로 401 이다 (로그인 필요)")
  void requiresLogin() throws Exception {
    given(checkInService.save(eq(DATE), any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    save("2026-10-17", "{\"source\":\"manual\"}").andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("모두 지우기는 204(본문 없음)이고, 로그인하지 않았으면 401 이다")
  void deletesAll() throws Exception {
    mockMvc.perform(delete("/api/v1/checkins")).andExpect(status().isNoContent());
    verify(checkInService).deleteAll();

    willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"))
        .given(checkInService)
        .deleteAll();
    mockMvc.perform(delete("/api/v1/checkins")).andExpect(status().isUnauthorized());
  }
}
