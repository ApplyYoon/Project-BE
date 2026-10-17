package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.domain.insight.dto.InsightReactionRequest;
import com.ithopeschool.projectbe.domain.insight.dto.InsightReactionResponse;
import com.ithopeschool.projectbe.domain.insight.dto.InsightStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 인사이트 반응 버튼 API 의 입구입니다. 요청을 받아 InsightReactionService 를 부르기만 합니다. (docs/API.md "반응 버튼")
// 주소는 첫 가설(InsightController)과 같은 /api/v1/insights 아래입니다. 둘 다 로그인이 필요합니다.
@Tag(name = "인사이트")
@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightReactionController {

  private final InsightReactionService insightReactionService;

  // 새로 누를 때도, 다시 눌러 바꿀 때도 200 을 돌려줍니다.
  @Operation(summary = "인사이트에 반응 누르기 (surprised · alreadyKnew · wrong · 다시 누르면 바뀜 · 내 것만 · 토큰 필요)")
  @PostMapping("/{id}/reaction")
  public InsightReactionResponse react(
      @PathVariable Long id, @Valid @RequestBody InsightReactionRequest request) {
    return insightReactionService.react(id, request);
  }

  @Operation(summary = "내 [몰랐는데 맞아] 비율 (반응을 누른 것 중에서 · 소수점 버림 · 토큰 필요)")
  @GetMapping("/stats")
  public InsightStatsResponse getStats() {
    return insightReactionService.getStats();
  }
}
