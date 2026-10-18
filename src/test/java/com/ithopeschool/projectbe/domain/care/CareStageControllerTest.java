package com.ithopeschool.projectbe.domain.care;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.care.dto.CareStageResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 테스트: 판정은 가짜 Service 로 두고, 주소 · 날짜 형식 · 응답 모양만 확인합니다.
@WebMvcTest(CareStageController.class)
class CareStageControllerTest {

  private static final CareStageResponse NUDGE =
      new CareStageResponse(LocalDate.of(2026, 10, 18), 2, "넛지", "toBase", false, "오늘은 가볍게 알려 줄게.");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CareStageService careStageService;

  @Test
  @DisplayName("POST /care-stages/today 는 200 과 오늘의 단계 · 이름 · 규칙 · 이유를 돌려준다")
  void today() throws Exception {
    given(careStageService.today()).willReturn(NUDGE);

    mockMvc
        .perform(post("/api/v1/care-stages/today"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-18"))
        .andExpect(jsonPath("$.stage").value(2))
        .andExpect(jsonPath("$.name").value("넛지"))
        .andExpect(jsonPath("$.rule").value("toBase"))
        .andExpect(jsonPath("$.suggestShrink").value(false))
        .andExpect(jsonPath("$.reason").value("오늘은 가볍게 알려 줄게."));
  }

  @Test
  @DisplayName("같은 순간에 다른 요청이 먼저 저장했으면(중복 저장) 한 번 더 불러 그 줄을 돌려준다")
  void todayRaceReadsAgain() throws Exception {
    given(careStageService.today())
        .willThrow(new DataIntegrityViolationException("uk_daily_care_stages_user_id_stage_date"))
        .willReturn(NUDGE);

    mockMvc
        .perform(post("/api/v1/care-stages/today"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value(2));
    verify(careStageService, times(2)).today();
  }

  @Test
  @DisplayName("GET /care-stages?from&to 는 기간 안의 기록을 날짜 순서로 돌려준다")
  void list() throws Exception {
    given(careStageService.list(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 18)))
        .willReturn(List.of(NUDGE));

    mockMvc
        .perform(get("/api/v1/care-stages").param("from", "2026-10-01").param("to", "2026-10-18"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].date").value("2026-10-18"))
        .andExpect(jsonPath("$[0].stage").value(2));
  }

  @Test
  @DisplayName("from 이 없거나 날짜 모양이 아니면 400 이고 Service 를 부르지 않는다")
  void listBadDate() throws Exception {
    mockMvc
        .perform(get("/api/v1/care-stages").param("to", "2026-10-18"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(get("/api/v1/care-stages").param("from", "10월").param("to", "2026-10-18"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(careStageService);
  }
}
