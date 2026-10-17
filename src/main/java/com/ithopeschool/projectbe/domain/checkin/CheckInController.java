package com.ithopeschool.projectbe.domain.checkin;

import com.ithopeschool.projectbe.domain.checkin.dto.CheckInResponse;
import com.ithopeschool.projectbe.domain.checkin.dto.CheckInSaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// "체크인" API 의 입구입니다. 요청을 받아 CheckInService 를 부르기만 합니다. 로그인이 필요합니다. (docs/API.md "체크인")
//
// @DateTimeFormat(ISO.DATE): "2026-10-17" 모양만 날짜로 받겠다는 표시입니다.
// 모양이 틀리면 GlobalExceptionHandler 가 400 VALIDATION_ERROR 로 바꿔 답합니다.
@Tag(name = "체크인")
@RestController
@RequestMapping("/api/v1/checkins")
@RequiredArgsConstructor
public class CheckInController {

  private final CheckInService checkInService;

  // 새로 만들 때도, 덮어쓸 때도 200 을 돌려줍니다. (FE 는 어느 쪽인지 신경 쓰지 않아도 됩니다)
  @Operation(summary = "그날의 체크인 저장 (같은 날 다시 보내면 덮어씀 · source 말고는 다 비어도 됨 · 토큰 필요)")
  @PutMapping("/{date}")
  public CheckInResponse save(
      @Parameter(description = "체크인한 날짜. 예: 2026-10-17", required = true)
          @PathVariable
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date,
      @Valid @RequestBody CheckInSaveRequest request) {
    return checkInService.save(date, request);
  }

  @Operation(summary = "기간 안의 내 체크인 목록 (날짜 순 · from~to 포함 · 안 한 날은 없음 · 31일까지 · 토큰 필요)")
  @GetMapping
  public List<CheckInResponse> getCheckIns(
      @Parameter(description = "시작 날짜(포함). 예: 2026-10-01", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "끝 날짜(포함). 예: 2026-10-07", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return checkInService.getCheckIns(from, to);
  }
}
