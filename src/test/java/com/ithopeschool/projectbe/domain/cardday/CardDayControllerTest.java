package com.ithopeschool.projectbe.domain.cardday;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.cardday.dto.CardDayMarkResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.CardMarkResultResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.FailRequest;
import com.ithopeschool.projectbe.domain.cardday.dto.PostponeLimitResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.PostponeRequest;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(CardDayService)은 가짜로 바꿉니다.
@WebMvcTest(CardDayController.class)
class CardDayControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CardDayService cardDayService;

  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);
  private static final String REASON_100 = "가".repeat(100);

  private static final CardDayMarkResponse POSTPONED =
      new CardDayMarkResponse(
          "3",
          "7",
          OCT_12,
          "postponed",
          REASON_100,
          null,
          OCT_12.plusDays(1),
          "20:00",
          1790000000000L);

  private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(cardDayService);
  }

  // ---- 미루기 ----

  @Test
  @DisplayName("미루기는 201 이고 표시 · 남은 횟수 · 안전 브레이크를 돌려준다 (이유의 앞뒤 공백은 지우고 넘김)")
  void postpone() throws Exception {
    given(cardDayService.postpone(eq(7L), any()))
        .willReturn(new CardMarkResultResponse(POSTPONED, 4, false));

    send(
            post("/api/v1/cards/7/postpone"),
            "{\"date\":\"2026-10-12\",\"reason\":\"  "
                + REASON_100
                + "  \",\"newDate\":\"2026-10-13\",\"newStartTime\":\"20:00\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mark.id").value("3"))
        .andExpect(jsonPath("$.mark.cardId").value("7"))
        .andExpect(jsonPath("$.mark.date").value("2026-10-12"))
        .andExpect(jsonPath("$.mark.mark").value("postponed"))
        .andExpect(jsonPath("$.mark.newDate").value("2026-10-13"))
        .andExpect(jsonPath("$.mark.newStartTime").value("20:00"))
        .andExpect(jsonPath("$.mark.cause").isEmpty())
        .andExpect(jsonPath("$.mark.length()").value(9))
        .andExpect(jsonPath("$.postponesLeft").value(4))
        .andExpect(jsonPath("$.safetyBrake").value(false));
    verify(cardDayService)
        .postpone(7L, new PostponeRequest(OCT_12, REASON_100, OCT_12.plusDays(1), "20:00"));
  }

  static Stream<Arguments> wrongPostpones() {
    String rest = ",\"newDate\":\"2026-10-13\",\"newStartTime\":\"20:00\"}";
    return Stream.of(
        // 공백을 지우면 99자라서 막힙니다.
        Arguments.of(
            "{\"date\":\"2026-10-12\",\"reason\":\" " + "가".repeat(99) + " \"" + rest,
            "왜 미루는지와 어떻게 할지를 100자 이상 500자 이하로 적어 주세요"),
        Arguments.of(
            "{\"date\":\"2026-10-12\",\"reason\":\"" + "가".repeat(501) + "\"" + rest,
            "왜 미루는지와 어떻게 할지를 100자 이상 500자 이하로 적어 주세요"),
        Arguments.of("{\"date\":\"2026-10-12\"" + rest, "왜 미루는지와 어떻게 할지를 적어 주세요"),
        Arguments.of("{\"reason\":\"" + REASON_100 + "\"" + rest, "어느 날의 카드를 미룰지 보내 주세요"),
        Arguments.of(
            "{\"date\":\"2026-10-12\",\"reason\":\""
                + REASON_100
                + "\",\"newStartTime\":\"20:00\"}",
            "새로 할 날짜를 보내 주세요"),
        Arguments.of(
            "{\"date\":\"2026-10-12\",\"reason\":\""
                + REASON_100
                + "\",\"newDate\":\"2026-10-13\",\"newStartTime\":\"8:00\"}",
            "새 시각은 19:00 처럼 보내 주세요"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("wrongPostpones")
  @DisplayName("미루기 요청이 틀리면 400 이고, Service 는 부르지 않는다")
  void postponeRejected(String body, String message) throws Exception {
    expectRejected(send(post("/api/v1/cards/7/postpone"), body), message);
  }

  @Test
  @DisplayName("한도를 넘으면 Service 가 던진 409 가 그대로 나간다")
  void postponeConflict() throws Exception {
    given(cardDayService.postpone(eq(9L), any()))
        .willThrow(new ApiException(ErrorCode.CONFLICT, "절대 카드는 미룰 수 없어요"));

    send(
            post("/api/v1/cards/9/postpone"),
            "{\"date\":\"2026-10-12\",\"reason\":\""
                + REASON_100
                + "\",\"newDate\":\"2026-10-13\",\"newStartTime\":\"20:00\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.message").value("절대 카드는 미룰 수 없어요"));
  }

  @Test
  @DisplayName("남은 미루기 횟수는 200 이고, 날짜를 안 보내면 400")
  void postponeLimit() throws Exception {
    given(cardDayService.getPostponeLimit(7L, OCT_12))
        .willReturn(new PostponeLimitResponse(2, null));

    mockMvc
        .perform(get("/api/v1/cards/7/postpone-limit").param("date", "2026-10-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.postponesLeft").value(2))
        .andExpect(jsonPath("$.blockReason").isEmpty());
    mockMvc.perform(get("/api/v1/cards/7/postpone-limit")).andExpect(status().isBadRequest());
  }

  // ---- 실패 ----

  @Test
  @DisplayName("실패로 기록은 201 이고, 원인이 없으면 400")
  void fail() throws Exception {
    CardDayMarkResponse failed =
        new CardDayMarkResponse("4", "7", OCT_12, "failed", null, "tired", null, null, 1L);
    given(cardDayService.fail(eq(7L), any()))
        .willReturn(new CardMarkResultResponse(failed, null, false));

    send(post("/api/v1/cards/7/fail"), "{\"date\":\"2026-10-12\",\"cause\":\"tired\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mark.mark").value("failed"))
        .andExpect(jsonPath("$.mark.cause").value("tired"))
        .andExpect(jsonPath("$.postponesLeft").isEmpty());
    verify(cardDayService).fail(7L, new FailRequest(OCT_12, "tired", null));
  }

  @Test
  @DisplayName("실패 원인을 안 보내면 400")
  void failRejected() throws Exception {
    expectRejected(
        send(post("/api/v1/cards/7/fail"), "{\"date\":\"2026-10-12\"}"), "실패 원인을 골라 주세요");
  }

  @Test
  @DisplayName("실패 사유가 500자를 넘으면 400")
  void failReasonTooLong() throws Exception {
    expectRejected(
        send(
            post("/api/v1/cards/7/fail"),
            "{\"date\":\"2026-10-12\",\"cause\":\"etc\",\"reason\":\"" + "가".repeat(501) + "\"}"),
        "실패 사유는 500자 이하로 적어 주세요");
  }

  // ---- 사후 입력 · 목록 ----

  @Test
  @DisplayName("사후 입력은 시작·끝 시각이 꼭 있어야 한다 (없으면 400)")
  void lateEntryRejected() throws Exception {
    expectRejected(
        send(
            post("/api/v1/cards/7/late-entry"),
            "{\"date\":\"2026-10-12\",\"endedAt\":1790003000000}"),
        "시작 시각을 보내 주세요");
  }

  @Test
  @DisplayName("표시 목록은 200 이고 배열이다. from · to 를 안 보내면 400")
  void marks() throws Exception {
    given(cardDayService.getMarks(OCT_12, OCT_12)).willReturn(List.of(POSTPONED));

    mockMvc
        .perform(
            get("/api/v1/card-day-marks").param("from", "2026-10-12").param("to", "2026-10-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].mark").value("postponed"));
    mockMvc
        .perform(get("/api/v1/card-day-marks").param("from", "2026-10-12"))
        .andExpect(status().isBadRequest());
  }
}
