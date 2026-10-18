package com.ithopeschool.projectbe.domain.calendar;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.calendar.dto.CalendarResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 테스트: 계산은 가짜 Service 로 두고, 주소 · 달 형식 · 응답 모양만 확인합니다.
@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CalendarService calendarService;

  @Test
  @DisplayName("month=2026-10 이면 200 과 날마다의 개수 · 감정 · 못 한 사유, 최근 4주 실행률을 돌려준다")
  void month() throws Exception {
    LocalDate oct1 = LocalDate.of(2026, 10, 1);
    given(calendarService.getMonth(YearMonth.of(2026, 10)))
        .willReturn(
            new CalendarResponse(
                "2026-10",
                List.of(
                    new CalendarResponse.Day(
                        oct1,
                        2,
                        1,
                        1,
                        0,
                        List.of("CALM"),
                        List.of(
                            new CalendarResponse.Miss(
                                "4", "밤 수학 오답 정리", "failed", "피곤·수면", null)))),
                new CalendarResponse.Recent(
                    LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 18), 40, 52, 77)));

    mockMvc
        .perform(get("/api/v1/calendar").param("month", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.month").value("2026-10"))
        .andExpect(jsonPath("$.days[0].date").value("2026-10-01"))
        .andExpect(jsonPath("$.days[0].done").value(2))
        .andExpect(jsonPath("$.days[0].missed").value(1))
        .andExpect(jsonPath("$.days[0].emotions[0]").value("CALM"))
        .andExpect(jsonPath("$.days[0].misses[0].cause").value("피곤·수면"))
        .andExpect(jsonPath("$.days[0].misses[0].note").isEmpty())
        .andExpect(jsonPath("$.recent.rate").value(77));
  }

  @Test
  @DisplayName("month 가 없거나 2026-10 모양이 아니면 400 이고, Service 는 부르지 않는다")
  void badMonth() throws Exception {
    mockMvc.perform(get("/api/v1/calendar")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/calendar").param("month", "2026-13"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/calendar").param("month", "10월"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(calendarService);
  }
}
