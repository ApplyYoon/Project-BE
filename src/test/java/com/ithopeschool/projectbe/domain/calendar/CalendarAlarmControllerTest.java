package com.ithopeschool.projectbe.domain.calendar;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.calendar.dto.CalendarAlarmSyncResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(CalendarAlarmService)은 가짜입니다.
@WebMvcTest(CalendarAlarmController.class)
class CalendarAlarmControllerTest {

  private static final String ONE_EVENT =
      "{\"events\":[{\"eventKey\":\"a\",\"title\":\"학원\",\"startsAt\":\"2026-10-24T10:00:00Z\"}]}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CalendarAlarmService calendarAlarmService;

  private ResultActions sync(String body) throws Exception {
    return mockMvc.perform(
        put("/api/v1/calendar-alarms").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("일정을 올리면 200 과 저장된 일정 수를 돌려준다")
  void syncs() throws Exception {
    given(calendarAlarmService.sync(any())).willReturn(new CalendarAlarmSyncResponse(1));

    sync(ONE_EVENT).andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(1));
  }

  @Test
  @DisplayName("목록이 없거나 제목 · id · 시각이 비었으면 400 이고 Service 를 부르지 않는다")
  void rejectsBadBody() throws Exception {
    sync("{}").andExpect(status().isBadRequest());
    sync("{\"events\":[{\"eventKey\":\"a\",\"title\":\" \",\"startsAt\":\"2026-10-24T10:00:00Z\"}]}")
        .andExpect(status().isBadRequest());
    sync("{\"events\":[{\"eventKey\":\"\",\"title\":\"학원\",\"startsAt\":\"2026-10-24T10:00:00Z\"}]}")
        .andExpect(status().isBadRequest());
    sync("{\"events\":[{\"eventKey\":\"a\",\"title\":\"학원\"}]}").andExpect(status().isBadRequest());

    verify(calendarAlarmService, never()).sync(any());
  }

  @Test
  @DisplayName("같은 순간에 올린 다른 요청과 겹치면(같은 일정 줄) 한 번 더 올려서 200 을 돌려준다")
  void retriesOnceWhenRequestsCollide() throws Exception {
    given(calendarAlarmService.sync(any()))
        .willThrow(new DataIntegrityViolationException("uk_calendar_alarms_user_id_event_key"))
        .willReturn(new CalendarAlarmSyncResponse(1));

    sync(ONE_EVENT).andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(1));
    verify(calendarAlarmService, times(2)).sync(any());
  }

  @Test
  @DisplayName("내 일정 알림을 모두 지우면 204 다")
  void clears() throws Exception {
    mockMvc.perform(delete("/api/v1/calendar-alarms")).andExpect(status().isNoContent());

    verify(calendarAlarmService).clear();
  }
}
