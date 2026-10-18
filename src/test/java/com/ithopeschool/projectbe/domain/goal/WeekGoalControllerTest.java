package com.ithopeschool.projectbe.domain.goal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.goal.dto.WeekGoalDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 테스트: 계산은 가짜 Service 로 두고, 주소 · 요청 검사 · 응답 모양만 확인합니다. (이슈 #88)
@WebMvcTest(WeekGoalController.class)
class WeekGoalControllerTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WeekGoalService weekGoalService;

  @Test
  @DisplayName("제안은 200 과 이번 주 월요일 · 후보 목록")
  void proposal() throws Exception {
    given(weekGoalService.propose())
        .willReturn(
            new WeekGoalDto.Proposal(
                MONDAY,
                List.of(
                    new WeekGoalDto.Candidate(
                        "completion", 60, 50, "완주율 60% 넘기기", "지난주 완주율은 50% 였어. 10%p 만 올려 보자."))));

    mockMvc
        .perform(get("/api/v1/weekly-goals/proposal"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weekStart").value("2026-10-19"))
        .andExpect(jsonPath("$.candidates[0].kind").value("completion"))
        .andExpect(jsonPath("$.candidates[0].title").value("완주율 60% 넘기기"));
  }

  @Test
  @DisplayName("저장은 200 과 저장한 목표. 주소의 날짜와 본문을 그대로 넘긴다")
  void save() throws Exception {
    given(weekGoalService.save(eq(MONDAY), any()))
        .willReturn(
            new WeekGoalDto.Item(
                MONDAY, "completion", 60, 50, "완주율 60% 넘기기", "unknown", null, 0, false));

    mockMvc
        .perform(
            put("/api/v1/weekly-goals/2026-10-19")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"completion\",\"target\":60,\"baseline\":50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("unknown"))
        .andExpect(jsonPath("$.judged").value(false));
    verify(weekGoalService).save(MONDAY, new WeekGoalDto.SaveRequest("completion", 60, 50));
  }

  @Test
  @DisplayName("저장 요청에 종류 · 숫자가 없거나 600 을 넘거나, 주소의 날짜 모양이 틀리면 400 이고 Service 는 부르지 않는다")
  void saveRejects() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/weekly-goals/2026-10-19")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"target\":60,\"baseline\":50}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("목표 종류를 골라 주세요"));
    mockMvc
        .perform(
            put("/api/v1/weekly-goals/2026-10-19")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"startDelay\",\"target\":601,\"baseline\":50}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("목표 숫자는 600 이하로 보내 주세요"));
    mockMvc
        .perform(
            put("/api/v1/weekly-goals/이번주")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"keep\",\"target\":60,\"baseline\":60}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(weekGoalService);
  }

  @Test
  @DisplayName("목록은 from · to 로 묻고 200 과 목표 배열, 날짜가 없으면 400")
  void list() throws Exception {
    given(weekGoalService.list(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        .willReturn(
            List.of(
                new WeekGoalDto.Item(
                    LocalDate.of(2026, 10, 12),
                    "completion",
                    65,
                    55,
                    "완주율 65% 넘기기",
                    "missed",
                    60,
                    92,
                    true)));

    mockMvc
        .perform(get("/api/v1/weekly-goals").param("from", "2026-10-01").param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("missed"))
        .andExpect(jsonPath("$[0].percent").value(92));
    mockMvc
        .perform(get("/api/v1/weekly-goals").param("from", "2026-10-01"))
        .andExpect(status().isBadRequest());
  }
}
