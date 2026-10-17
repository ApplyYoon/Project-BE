package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.ChecklistRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ChecklistResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 작은 AI 기능(입력 칸을 AI 가 먼저 채우기)의 입구입니다. 요청을 받아 Service 를 부르기만 합니다. (이슈 #236 · #237)
// 모두 저장하지 않고 제안만 돌려주므로 200 입니다. AI 가 못 만들면 에러 대신 빈 칸(null)이고, FE 가 기본값을 씁니다.
// 로그인이 필요합니다. (docs/API.md "AI 도우미")
@Tag(name = "AI 도우미")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

  private final ChecklistService checklistService;

  @Operation(
      summary = "카드 체크리스트 추천: 카드 제목 → 항목 3~6개 (저장 안 함 · 내 지난 기록을 봄 · 못 만들면 items: null · 토큰 필요)")
  @PostMapping("/checklist")
  public ChecklistResponse checklist(@Valid @RequestBody ChecklistRequest request) {
    return checklistService.suggest(request);
  }
}
