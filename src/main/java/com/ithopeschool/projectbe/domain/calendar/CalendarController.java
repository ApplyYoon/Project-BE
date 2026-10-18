package com.ithopeschool.projectbe.domain.calendar;

import com.ithopeschool.projectbe.domain.calendar.dto.CalendarResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 달력 API 입니다. 계산은 CalendarService 가 하고, 여기서는 받은 달을 넘기기만 합니다. (이슈 #235)
@Tag(name = "달력")
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

  private final CalendarService calendarService;

  @Operation(
      summary =
          "한 달 달력: 날마다 실행 · 지연 · 미실행 · 휴식 개수 · 감정 태그 · 못 한 사유와 최근 4주 실행률 (month=2026-10 · 토큰 필요)")
  @GetMapping
  public CalendarResponse getMonth(
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    return calendarService.getMonth(month);
  }
}
