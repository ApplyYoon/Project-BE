package com.ithopeschool.projectbe.domain.insight;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.insight.dto.HypothesesResponse;
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

// Controller 만 띄워서 주소와 응답 모양(JSON 이름·날짜 글자)을 확인합니다. 진짜 로직(InsightService)은 가짜입니다.
@WebMvcTest(InsightController.class)
class InsightControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InsightService insightService;

  @Test
  @DisplayName("GET /api/v1/insights/hypotheses 는 200 과 가설 · 카운트다운을 돌려준다")
  void returnsHypotheses() throws Exception {
    given(insightService.getHypotheses())
        .willReturn(
            new HypothesesResponse(
                List.of(
                    new HypothesesResponse.Hypothesis(
                        "pastFailureCauses",
                        "weekend",
                        "주말에 무너지는 편이라고 했지? 2주 동안 평일과 주말을 비교해서 확인해볼게.",
                        "rhythm",
                        37)),
                null,
                new HypothesesResponse.FirstReport(LocalDate.of(2026, 10, 15), 14, 3, 11, false)));

    mockMvc
        .perform(get("/api/v1/insights/hypotheses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hypotheses[0].answerType").value("pastFailureCauses"))
        .andExpect(jsonPath("$.hypotheses[0].answer").value("weekend"))
        .andExpect(jsonPath("$.hypotheses[0].analysis").value("rhythm"))
        .andExpect(jsonPath("$.hypotheses[0].analysisIssue").value(37))
        .andExpect(jsonPath("$.defaultSentence").doesNotExist())
        .andExpect(jsonPath("$.firstReport.firstRecordDate").value("2026-10-15"))
        .andExpect(jsonPath("$.firstReport.totalDays").value(14))
        .andExpect(jsonPath("$.firstReport.daysLeft").value(11))
        .andExpect(jsonPath("$.firstReport.ready").value(false));
  }

  @Test
  @DisplayName("로그인하지 않았으면 401")
  void unauthorized() throws Exception {
    given(insightService.getHypotheses())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/insights/hypotheses"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }
}
