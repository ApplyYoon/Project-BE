package com.ithopeschool.projectbe.domain.suggestion;

import com.ithopeschool.projectbe.domain.suggestion.dto.ImprovementSuggestionsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 개선안 API 의 입구입니다. 요청을 받아 ImprovementService 를 부르기만 합니다. (이슈 #51, docs/API.md "개선안")
// 로그인이 필요합니다.
@Tag(name = "개선안")
@RestController
@RequestMapping("/api/v1/improvements")
@RequiredArgsConstructor
public class ImprovementController {

  private final ImprovementService improvementService;

  @Operation(
      summary = "개선안 3개 제안: 내 실패 원인에 맞는 전략 쉬움·보통·강함 (고르는 것은 코드 · AI 는 문장만 다듬음 · 저장 안 함 · 토큰 필요)")
  @GetMapping("/suggestions")
  public ImprovementSuggestionsResponse suggestions() {
    return improvementService.suggest();
  }
}
