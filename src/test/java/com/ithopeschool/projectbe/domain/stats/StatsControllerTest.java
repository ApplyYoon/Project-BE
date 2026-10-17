package com.ithopeschool.projectbe.domain.stats;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.stats.dto.MissReasonStatsResponse;
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

// Controller 만 띄워서 주소와 응답 모양(JSON 이름·날짜 글자)을 확인합니다. 진짜 로직(MissReasonService)은 가짜입니다.
@WebMvcTest(StatsController.class)
class StatsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MissReasonService missReasonService;

  @Test
  @DisplayName("GET /api/v1/stats/miss-reasons 는 200 과 14일 · 30일 결과를 돌려준다")
  void returnsMissReasons() throws Exception {
    MissReasonStatsResponse.Period last14 =
        new MissReasonStatsResponse.Period(
            LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 10, 16),
            2,
            List.of(
                new MissReasonStatsResponse.Reason(
                    "phone",
                    1,
                    List.of(0, 0, 0, 1, 0, 0, 0),
                    1.0,
                    0,
                    List.of(LocalDate.of(2026, 10, 15)))),
            List.of("phone"));
    MissReasonStatsResponse.Period last30 =
        new MissReasonStatsResponse.Period(
            LocalDate.of(2026, 9, 17), LocalDate.of(2026, 10, 16), 0, List.of(), List.of());
    given(missReasonService.getMissReasons())
        .willReturn(new MissReasonStatsResponse(last14, last30));

    mockMvc
        .perform(get("/api/v1/stats/miss-reasons"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.last14Days.from").value("2026-10-03"))
        .andExpect(jsonPath("$.last14Days.to").value("2026-10-16"))
        .andExpect(jsonPath("$.last14Days.missedDayCount").value(2))
        .andExpect(jsonPath("$.last14Days.topReasons[0]").value("phone"))
        .andExpect(jsonPath("$.last14Days.reasons[0].reason").value("phone"))
        .andExpect(jsonPath("$.last14Days.reasons[0].dayCount").value(1))
        .andExpect(jsonPath("$.last14Days.reasons[0].weekdayCounts[3]").value(1))
        .andExpect(jsonPath("$.last14Days.reasons[0].averageRecoveryDays").value(1.0))
        .andExpect(jsonPath("$.last14Days.reasons[0].notRecoveredCount").value(0))
        .andExpect(jsonPath("$.last14Days.reasons[0].dates[0]").value("2026-10-15"))
        .andExpect(jsonPath("$.last30Days.from").value("2026-09-17"))
        .andExpect(jsonPath("$.last30Days.reasons").isEmpty());
  }

  @Test
  @DisplayName("로그인하지 않았으면 401")
  void unauthorized() throws Exception {
    given(missReasonService.getMissReasons())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/stats/miss-reasons"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }
}
