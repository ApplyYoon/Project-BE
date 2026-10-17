package com.ithopeschool.projectbe.domain.insight;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인사이트")
@RestController
@RequiredArgsConstructor
public class ScoreStudyController {
  private final ScoreStudyService service;

  @Operation(summary = "과목별 시험 전 2주 순공과 점수 변화 조회")
  @GetMapping("/api/v1/insights/score-study")
  public List<ScoreStudyService.Subject> getAnalysis() {
    return service.getAnalysis();
  }
}
