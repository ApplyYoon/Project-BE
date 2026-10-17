package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.ChecklistRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ChecklistResponse;
import com.ithopeschool.projectbe.domain.ai.dto.FailCauseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FailCauseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepResponse;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardRequest;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardResponse;
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
  private final FirstStepService firstStepService;
  private final FailCauseService failCauseService;
  private final RoutineCardService routineCardService;

  @Operation(
      summary = "카드 체크리스트 추천: 카드 제목 → 항목 3~6개 (저장 안 함 · 내 지난 기록을 봄 · 못 만들면 items: null · 토큰 필요)")
  @PostMapping("/checklist")
  public ChecklistResponse checklist(@Valid @RequestBody ChecklistRequest request) {
    return checklistService.suggest(request);
  }

  @Operation(
      summary =
          "첫 준비 행동: 체크리스트 첫 항목 → 아주 작은 행동 하나 ([더 작게] 는 level 1·2 · 저장 안 함 · 못 만들면 step: null · 토큰 필요)")
  @PostMapping("/first-step")
  public FirstStepResponse firstStep(@Valid @RequestBody FirstStepRequest request) {
    return firstStepService.suggest(request);
  }

  @Operation(
      summary =
          "예상 실패 원인 선지: 카드 제목 → 선지 3개 (내 최근 30일 실패 원인으로 고름 · 내 행동 원인 1개 이상 · 기록이 모자라면 options: null · 토큰 필요)")
  @PostMapping("/fail-cause-options")
  public FailCauseResponse failCauseOptions(@Valid @RequestBody FailCauseRequest request) {
    return failCauseService.suggest(request);
  }

  @Operation(
      summary =
          "루틴 카드 제안: 목표 + 방해물 + 대처 → 루틴 카드 2~3장 (요일·시각·길이는 코드가 정함 · 저장 안 함 · 못 만들면 cards: null · 토큰 필요)")
  @PostMapping("/routine-cards")
  public RoutineCardResponse routineCards(@Valid @RequestBody RoutineCardRequest request) {
    return routineCardService.suggest(request);
  }
}
