package com.ithopeschool.projectbe.domain.insight;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.insight.dto.InsightReactionResponse;
import com.ithopeschool.projectbe.domain.insight.dto.InsightStatsResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(InsightReactionService)은 가짜입니다.
@WebMvcTest(InsightReactionController.class)
class InsightReactionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InsightReactionService insightReactionService;

  private ResultActions react(String id, String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/insights/" + id + "/reaction")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  @DisplayName("반응을 보내면 200 과 저장된 반응이다")
  void reacts() throws Exception {
    given(insightReactionService.react(eq(3L), any()))
        .willReturn(new InsightReactionResponse("3", "surprised"));

    react("3", "{\"response\":\"surprised\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("3"))
        .andExpect(jsonPath("$.response").value("surprised"));
  }

  @Test
  @DisplayName("반응이 없거나 비었거나, 주소의 번호가 숫자가 아니면 400 이고 서비스를 부르지 않는다")
  void rejectsBadRequests() throws Exception {
    react("3", "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("반응을 골라 주세요"));
    react("3", "{\"response\":\"\"}").andExpect(status().isBadRequest());
    react("abc", "{\"response\":\"surprised\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(insightReactionService);
  }

  @Test
  @DisplayName("남의 인사이트면 서비스가 던진 404 를 그대로 돌려준다")
  void othersInsightIs404() throws Exception {
    given(insightReactionService.react(eq(99L), any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "인사이트를 찾을 수 없어요"));

    react("99", "{\"response\":\"surprised\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("통계는 200 과 반응 수 · 몰랐는데 맞아 수 · 비율이다")
  void stats() throws Exception {
    given(insightReactionService.getStats()).willReturn(new InsightStatsResponse(4, 3, 75));

    mockMvc
        .perform(get("/api/v1/insights/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reactedCount").value(4))
        .andExpect(jsonPath("$.surprisedCount").value(3))
        .andExpect(jsonPath("$.surprisedPercent").value(75));
  }

  @Test
  @DisplayName("아직 반응이 없으면 비율은 null 이다")
  void statsWithoutReactions() throws Exception {
    given(insightReactionService.getStats()).willReturn(new InsightStatsResponse(0, 0, null));

    mockMvc
        .perform(get("/api/v1/insights/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.surprisedPercent").doesNotExist());
  }
}
