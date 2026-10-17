package com.ithopeschool.projectbe.domain.cardresult;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.cardresult.dto.CardDayResultResponse;
import com.ithopeschool.projectbe.domain.cardresult.dto.PlanPatternResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소 · 상태 번호 · 응답 모양을 확인합니다. 진짜 로직(CardResultService)은 가짜로 바꿉니다.
@WebMvcTest(CardResultController.class)
class CardResultControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CardResultService cardResultService;

  private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);

  @Test
  @DisplayName("결과 목록은 200 이고 여덟 칸 모양이다 (판정 전이면 result 가 null)")
  void results() throws Exception {
    given(cardResultService.getResults(OCT_1, OCT_12))
        .willReturn(
            List.of(
                new CardDayResultResponse("7", OCT_12, "late", 0.75, "execution", 0.5, -1, false),
                new CardDayResultResponse("8", OCT_12, null, 0, null, null, null, null)));

    mockMvc
        .perform(get("/api/v1/cards/results").param("from", "2026-10-01").param("to", "2026-10-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].length()").value(8))
        .andExpect(jsonPath("$[0].cardId").value("7"))
        .andExpect(jsonPath("$[0].date").value("2026-10-12"))
        .andExpect(jsonPath("$[0].result").value("late"))
        .andExpect(jsonPath("$[0].runRate").value(0.75))
        .andExpect(jsonPath("$[0].planAxis").value("execution"))
        .andExpect(jsonPath("$[0].checklistRate").value(0.5))
        .andExpect(jsonPath("$[0].predictionGap").value(-1))
        .andExpect(jsonPath("$[0].startedOnTime").value(false))
        .andExpect(jsonPath("$[1].result").isEmpty());
  }

  @Test
  @DisplayName("from · to 가 없거나 모양이 틀리면 400 이고 Service 는 부르지 않는다")
  void wrongDates() throws Exception {
    mockMvc
        .perform(get("/api/v1/cards/results").param("from", "2026-10-01"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/cards/results").param("from", "2026-13-01").param("to", "2026-10-12"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(cardResultService);
  }

  @Test
  @DisplayName("기간이 31일을 넘으면 Service 가 던진 400 이 그대로 나간다")
  void tooLong() throws Exception {
    given(cardResultService.getResults(OCT_1, LocalDate.of(2026, 11, 1)))
        .willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "한 번에 31일까지만 조회할 수 있어요"));

    mockMvc
        .perform(get("/api/v1/cards/results").param("from", "2026-10-01").param("to", "2026-11-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("한 번에 31일까지만 조회할 수 있어요"));
  }

  @Test
  @DisplayName("계획 패턴은 200 이고 { goalId, from, to, sampleCount, checklistRate, pattern } 이다")
  void planPattern() throws Exception {
    given(cardResultService.getPlanPattern(12L))
        .willReturn(
            new PlanPatternResponse("12", LocalDate.of(2026, 9, 15), OCT_12, 5, 0.8, "fit"));

    mockMvc
        .perform(get("/api/v1/cards/plan-pattern").param("goalId", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$.goalId").value("12"))
        .andExpect(jsonPath("$.from").value("2026-09-15"))
        .andExpect(jsonPath("$.to").value("2026-10-12"))
        .andExpect(jsonPath("$.sampleCount").value(5))
        .andExpect(jsonPath("$.checklistRate").value(0.8))
        .andExpect(jsonPath("$.pattern").value("fit"));
  }

  @Test
  @DisplayName("goalId 가 없거나 숫자가 아니면 400 이고 Service 는 부르지 않는다")
  void wrongGoalId() throws Exception {
    mockMvc.perform(get("/api/v1/cards/plan-pattern")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/cards/plan-pattern").param("goalId", "수학"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(cardResultService);
  }
}
