package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.stats.dto.MissReasonStatsResponse;
import com.ithopeschool.projectbe.domain.stats.dto.RhythmStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// "분석" API 의 입구입니다. 로그인이 필요합니다. (docs/API.md "분석")
@Tag(name = "분석")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

  private final MissReasonService missReasonService;
  private final RhythmService rhythmService;

  @Operation(summary = "실패 원인 (최근 14일·30일, 어제까지 · 주요 원인 · 요일 분포 · 회복 속도 · 토큰 필요)")
  @GetMapping("/miss-reasons")
  public MissReasonStatsResponse getMissReasons() {
    return missReasonService.getMissReasons();
  }

  @Operation(summary = "리듬·패턴 (최근 30일, 어제까지 · 며칠째 무너짐·회복 · 요일·시간대 효율 · 시험·약속 다음 날 · 토큰 필요)")
  @GetMapping("/rhythm")
  public RhythmStatsResponse getRhythm() {
    return rhythmService.getRhythm();
  }
}
