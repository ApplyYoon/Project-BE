package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.domain.insight.dto.HypothesesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// "인사이트" API 의 입구입니다. 로그인이 필요합니다. (docs/API.md "인사이트")
@Tag(name = "인사이트")
@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightController {

  private final InsightService insightService;

  @Operation(summary = "첫 가설 (온보딩 설문 답으로 만든 2주 동안 확인할 가설 + 첫 리포트까지 남은 날)")
  @GetMapping("/hypotheses")
  public HypothesesResponse getHypotheses() {
    return insightService.getHypotheses();
  }
}
