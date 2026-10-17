package com.ithopeschool.projectbe.domain.thinkingtest;

import com.ithopeschool.projectbe.domain.thinkingtest.dto.ExamThinkingGapResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingGapResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingTestResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingTestSaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// "생각 습관 테스트" API 의 입구입니다. 네 주소 모두 로그인이 필요합니다. (docs/API.md "생각 습관 테스트")
@Tag(name = "생각 습관 테스트")
@RestController
@RequestMapping("/api/v1/thinking-tests")
@RequiredArgsConstructor
public class ThinkingTestController {

  private final ThinkingTestService thinkingTestService;

  @Operation(summary = "테스트 결과 저장 (습관 8개 평균 · 약점 칩 · 다시 하면 새로 쌓임)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ThinkingTestResponse save(@Valid @RequestBody ThinkingTestSaveRequest request) {
    return thinkingTestService.save(request);
  }

  @Operation(summary = "내 가장 최근 테스트 결과 (공부 스타일 · 많이 해당하는 습관 · 아직 안 했으면 404)")
  @GetMapping("/latest")
  public ThinkingTestResponse getLatest() {
    return thinkingTestService.getLatest();
  }

  @Operation(summary = "내 생각 vs 현실 (테스트 결과 ↔ 최근 통화의 못 채운 이유·보인 습관)")
  @GetMapping("/gap")
  public ThinkingGapResponse getGap(
      @Parameter(description = "며칠 동안의 통화로 볼지 (7 ~ 60, 기본 14)") @RequestParam(required = false)
          Integer days) {
    return thinkingTestService.getGap(days);
  }

  @Operation(summary = "시험 전 2주의 내 생각 vs 현실 (약점 칩 ↔ 그만두거나 못 채운 이유 · 계획 낙관 ↔ 계획 대비 실제)")
  @GetMapping("/exam-gap")
  public ExamThinkingGapResponse getExamGap(
      @Parameter(description = "시험 날짜. 이 날의 14일 전 ~ 하루 전 기록을 봅니다. 예: 2026-10-20", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate examDate) {
    return thinkingTestService.getExamGap(examDate);
  }
}
