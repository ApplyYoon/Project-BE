package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.school.dto.SchoolEventsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 내 학교 학사일정의 입구입니다. 요청을 받아 Service 를 부르기만 합니다. (이슈 #282)
// 로그인이 필요합니다. 학교 번호는 가입 정보(accounts.school_code)에서 꺼내서, 다른 학교를 물을 수 없습니다.
@Tag(name = "학교 학사일정")
@RestController
@RequestMapping("/api/v1/school/events")
@RequiredArgsConstructor
public class SchoolEventController {

  private final SchoolEventService schoolEventService;

  @Operation(
      summary =
          "내 학교 학사일정 (NEIS · 시험·방학·행사 · 24시간마다 새로 받음 · 날짜를 안 주면 오늘부터 120일 · 학교를 안 골랐으면 school: null · 토큰 필요)")
  @GetMapping
  public SchoolEventsResponse events(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return schoolEventService.getMyEvents(from, to);
  }

  @Operation(summary = "[새로고침] NEIS 에서 다시 받기 (같은 학교는 10분에 한 번만 · 오늘부터 120일 · 토큰 필요)")
  @PostMapping("/refresh")
  public SchoolEventsResponse refresh() {
    return schoolEventService.refreshMyEvents();
  }
}
