package com.ithopeschool.projectbe.domain.session;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "목표 시간 추천")
@RestController
@RequestMapping("/api/v1/study-sessions/target-recommendation")
@RequiredArgsConstructor
public class TargetRecommendationController {
  private final TargetRecommendationService service;

  @Operation(summary = "내 과목의 최근 공부 기록 3개로 목표 추천 (제안만 · 자동 변경 없음 · 토큰 필요)")
  @GetMapping
  public TargetRecommendationService.Response recommend(
      @RequestParam Long subjectId, @RequestParam int currentTargetMinutes) {
    return service.recommend(subjectId, currentTargetMinutes);
  }
}
