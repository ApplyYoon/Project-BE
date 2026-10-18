package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.care.dto.CareStageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 관리 단계 API 입니다. 판정은 CareStageService 가 하고, 여기서는 넘기기만 합니다. (이슈 #62)
@Tag(name = "관리 단계")
@RestController
@RequestMapping("/api/v1/care-stages")
@RequiredArgsConstructor
public class CareStageController {

  private final CareStageService careStageService;

  // 처음 부른 날에는 판정해서 저장하므로 POST 입니다. 같은 날 다시 부르면 저장된 것을 그대로 돌려줍니다.
  @Operation(summary = "오늘의 관리 단계 0~4 와 이유 한 줄 (하루 한 번 판정해 저장 · 이미 했으면 그대로 · 토큰 필요)")
  @PostMapping("/today")
  public CareStageResponse today() {
    try {
      return careStageService.today();
    } catch (DataIntegrityViolationException e) {
      // 같은 순간에 두 번 불러 다른 요청(또는 새벽 예약 작업)이 먼저 저장했으면, 다시 불러 그 줄을 돌려줍니다.
      return careStageService.today();
    }
  }

  @Operation(summary = "지난 관리 단계 기록 (from=2026-10-01&to=2026-10-18 · 최대 92일 · 토큰 필요)")
  @GetMapping
  public List<CareStageResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return careStageService.list(from, to);
  }
}
