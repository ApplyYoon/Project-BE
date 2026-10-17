package com.ithopeschool.projectbe.domain.risk;

import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastListResponse;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastRequest;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// "위험 예측" API 의 입구입니다. 세 주소 모두 로그인이 필요합니다. (docs/API.md "위험 예측")
// 날짜는 "2026-10-13" 모양만 받습니다. 모양이 틀리거나 빠지면 Spring 이 막고,
// GlobalExceptionHandler 가 400 VALIDATION_ERROR 로 바꿔 답합니다.
@Tag(name = "위험 예측 (무너질 가능성이 높은 날)")
@RestController
@RequestMapping("/api/v1/risk-forecasts")
@RequiredArgsConstructor
public class RiskController {

  private final RiskService riskService;

  // 줄을 만들거나 고치는 요청이라 GET 이 아니라 POST 입니다. (GET 으로 저장하면 동의 검사를 피해 갈 수 있습니다)
  // 새로 만들 때도, 고칠 때도 200 을 돌려줍니다. (FE 는 어느 쪽인지 신경 쓰지 않아도 됩니다)
  @Operation(summary = "오늘이나 내일의 위험 예측하기 (같은 날 다시 하면 최신 결과로 고침 · 위험 없음도 기록)")
  @PostMapping
  public RiskForecastResponse forecast(@Valid @RequestBody RiskForecastRequest request) {
    return riskService.forecast(request);
  }

  @Operation(summary = "그날 기록된 내 위험 예측 하나 (안 했으면 404)")
  @GetMapping("/{date}")
  public RiskForecastResponse get(
      @Parameter(description = "예측한 날짜. 예: 2026-10-13")
          @PathVariable
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return riskService.get(date);
  }

  @Operation(summary = "기간의 내 위험 예측 목록과 맞은 개수 (최대 62일 · 날짜 순)")
  @GetMapping
  public RiskForecastListResponse list(
      @Parameter(description = "시작 날짜(포함). 예: 2026-10-01", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "끝 날짜(포함). 예: 2026-10-31", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return riskService.list(from, to);
  }
}
