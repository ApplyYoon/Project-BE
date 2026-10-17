package com.ithopeschool.projectbe.domain.school;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.school.dto.SchoolEventsResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소 · 날짜 받기 · 응답 모양을 봅니다. 진짜 로직(SchoolEventService)은 가짜입니다.
@WebMvcTest(SchoolEventController.class)
class SchoolEventControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SchoolEventService schoolEventService;

  private static final SchoolEventsResponse RESPONSE =
      SchoolEventsResponse.of(
          "7530560",
          "한국디지털미디어고등학교",
          Instant.ofEpochMilli(1000),
          List.of(
              new SchoolEventsResponse.Event(
                  LocalDate.of(2026, 12, 9), "2학기 2차 정기시험", null, List.of(1, 2), true)));

  @Test
  @DisplayName("GET 은 from · to 를 날짜로 받아 서비스에 넘기고 일정 목록을 돌려준다")
  void getsEvents() throws Exception {
    given(schoolEventService.getMyEvents(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)))
        .willReturn(RESPONSE);

    mockMvc
        .perform(get("/api/v1/school/events").param("from", "2026-12-01").param("to", "2026-12-31"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"school\":{\"code\":\"7530560\",\"name\":\"한국디지털미디어고등학교\"},\"fetchedAt\":1000,"
                        + "\"events\":[{\"date\":\"2026-12-09\",\"name\":\"2학기 2차 정기시험\",\"content\":null,"
                        + "\"grades\":[1,2],\"exam\":true}]}",
                    true));
  }

  @Test
  @DisplayName("날짜를 안 주면 비워서 넘긴다 (서비스가 오늘부터 120일로 정함) · 학교가 없으면 school: null")
  void defaultsAndNoSchool() throws Exception {
    given(schoolEventService.getMyEvents(null, null)).willReturn(SchoolEventsResponse.noSchool());

    mockMvc
        .perform(get("/api/v1/school/events"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"school\":null,\"fetchedAt\":null,\"events\":[]}", true));
    verify(schoolEventService).getMyEvents(null, null);
  }

  @Test
  @DisplayName("POST /refresh 는 새로고침한 일정을 돌려준다")
  void refreshes() throws Exception {
    given(schoolEventService.refreshMyEvents()).willReturn(RESPONSE);

    mockMvc.perform(post("/api/v1/school/events/refresh")).andExpect(status().isOk());
    verify(schoolEventService).refreshMyEvents();
  }
}
