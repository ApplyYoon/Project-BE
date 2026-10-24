package com.ithopeschool.projectbe.domain.calendar;

import com.ithopeschool.projectbe.domain.calendar.dto.CalendarAlarmSyncRequest;
import com.ithopeschool.projectbe.domain.calendar.dto.CalendarAlarmSyncResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// "구글 캘린더 일정 알림" API 의 입구입니다. 요청을 받아 CalendarAlarmService 를 부르기만 합니다. 로그인이 필요합니다.
// 알림을 실제로 보내는 것은 예약 작업 CalendarAlarmJob(POST /api/v1/jobs/calendar-alarm)입니다.
@Tag(name = "구글 캘린더 알림")
@RestController
@RequestMapping("/api/v1/calendar-alarms")
@RequiredArgsConstructor
public class CalendarAlarmController {

  private final CalendarAlarmService calendarAlarmService;

  @Operation(summary = "앞으로의 구글 캘린더 일정 올리기 (덮어쓰기 · 시작 10분 전에 푸시 · 토큰 필요)")
  @PutMapping
  public CalendarAlarmSyncResponse sync(@Valid @RequestBody CalendarAlarmSyncRequest request) {
    try {
      return calendarAlarmService.sync(request);
    } catch (DataIntegrityViolationException e) {
      // 같은 순간에 두 번 올려서(탭 두 개 등) 다른 요청이 같은 일정을 먼저 저장했으면,
      // 한 번 더 올립니다. 이번에는 그 줄을 찾아서 고치기만 합니다. (CareStageController 와 같은 방식)
      return calendarAlarmService.sync(request);
    }
  }

  // 지운 뒤에는 돌려줄 것이 없어서 204(본문 없음)입니다.
  @Operation(summary = "내 구글 캘린더 일정 알림 모두 지우기 (캘린더 연결 끊기 · 토큰 필요)")
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clear() {
    calendarAlarmService.clear();
  }
}
