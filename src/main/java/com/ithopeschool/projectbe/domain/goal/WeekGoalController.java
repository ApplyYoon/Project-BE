package com.ithopeschool.projectbe.domain.goal;

import com.ithopeschool.projectbe.domain.goal.dto.WeekGoalDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 이번 주 목표 API 입니다. 계산은 WeekGoalService 가 하고, 여기서는 받은 값을 넘기기만 합니다. (이슈 #88)
@Tag(name = "이번 주 목표")
@RestController
@RequestMapping("/api/v1/weekly-goals")
@RequiredArgsConstructor
public class WeekGoalController {

  private final WeekGoalService weekGoalService;

  @Operation(
      summary =
          "이번 주 목표 제안 (지난주 기록으로 후보 여러 개 · 맨 앞이 먼저 · 기록이 모자라거나 안전 브레이크 · 시험 기간이면 빈 배열 · 토큰 필요)")
  @GetMapping("/proposal")
  public WeekGoalDto.Proposal propose() {
    return weekGoalService.propose();
  }

  @Operation(summary = "이번 주(또는 다음 주) 목표 저장 (한 주에 하나 · 다시 보내면 덮어씀 · weekStart 는 월요일 · 토큰 필요)")
  @PutMapping("/{weekStart}")
  public WeekGoalDto.Item save(
      @PathVariable LocalDate weekStart, @Valid @RequestBody WeekGoalDto.SaveRequest request) {
    return weekGoalService.save(weekStart, request);
  }

  @Operation(summary = "주 목표 목록과 달성 여부 (from ~ to 사이에 시작한 주 · 판정 전인 주는 지금까지의 기록으로 · 토큰 필요)")
  @GetMapping
  public List<WeekGoalDto.Item> list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return weekGoalService.list(from, to);
  }
}
