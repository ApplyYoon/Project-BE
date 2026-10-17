package com.ithopeschool.projectbe.domain.restday;

import com.ithopeschool.projectbe.domain.restday.dto.RestDayResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 휴식 처리 API 의 입구입니다. RestDayService 를 부르기만 합니다. (docs/API.md "휴식 처리", 이슈 #225)
// 두 주소 모두 로그인이 필요합니다.
@Tag(name = "휴식 처리")
@RestController
@RequestMapping("/api/v1/rest-days")
@RequiredArgsConstructor
public class RestDayController {

  private final RestDayService restDayService;

  // 주소의 {date} 가 "2026-10-12" 모양이 아니면 Spring 이 막고 400 으로 답합니다.
  @Operation(summary = "그날을 휴식으로 처리 (지난 날·오늘만 · 한 달 4번까지 · 토큰 필요)")
  @PostMapping("/{date}")
  @ResponseStatus(HttpStatus.CREATED)
  public RestDayResponse rest(
      @Parameter(description = "휴식으로 처리할 날짜. 예: 2026-10-12")
          @PathVariable
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return restDayService.rest(date);
  }

  @Operation(summary = "기간 안의 내 휴식 날 목록 (from~to 포함 · 31일까지 · 토큰 필요)")
  @GetMapping
  public List<RestDayResponse> getRestDays(
      @Parameter(description = "시작 날짜(포함). 예: 2026-10-01", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "끝 날짜(포함). 예: 2026-10-31", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return restDayService.getRestDays(from, to);
  }
}
