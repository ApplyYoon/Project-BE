package com.ithopeschool.projectbe.domain.calendar.dto;

import com.ithopeschool.projectbe.domain.calendar.CalendarAlarm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

// 구글 캘린더 일정 올리기 요청입니다. 보낸 목록이 "앞으로의 내 일정 전부" 가 됩니다. (빈 목록이면 다 지움)
//
//   { "events": [ { "eventKey": "abc123", "title": "학원", "startsAt": "2026-10-24T10:00:00Z" } ] }
//
// FE 는 시각이 있는 일정만 보냅니다. (하루 종일 일정은 "10분 전" 이 없어서 빼고 보냄)
public record CalendarAlarmSyncRequest(
    @NotNull(message = "일정 목록(events)을 보내 주세요")
        @Size(max = CalendarAlarmSyncRequest.MAX_EVENTS, message = "일정은 한 번에 200개까지 보낼 수 있어요")
        List<@Valid @NotNull Event> events) {

  // 한 번에 받는 일정 수. 7일 치로 넉넉한 값입니다.
  public static final int MAX_EVENTS = 200;

  // 일정 하나
  public record Event(
      @NotBlank(message = "일정 id(eventKey)를 보내 주세요")
          @Size(max = CalendarAlarm.MAX_EVENT_KEY_LENGTH, message = "일정 id 가 너무 길어요")
          String eventKey,
      @NotBlank(message = "일정 제목을 보내 주세요")
          @Size(max = CalendarAlarm.MAX_TITLE_LENGTH, message = "일정 제목은 100자까지 보낼 수 있어요")
          String title,
      @NotNull(message = "시작 시각(startsAt)을 보내 주세요") Instant startsAt) {}
}
