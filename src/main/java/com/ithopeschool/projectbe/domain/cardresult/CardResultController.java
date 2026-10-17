package com.ithopeschool.projectbe.domain.cardresult;

import com.ithopeschool.projectbe.domain.cardresult.dto.CardDayResultResponse;
import com.ithopeschool.projectbe.domain.cardresult.dto.PlanPatternResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 카드 결과 API 의 입구입니다. CardResultService 를 부르기만 합니다. (이슈 #229, docs/API.md "카드 결과")
// 두 주소 모두 로그인이 필요합니다.
@Tag(name = "카드 결과")
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardResultController {

  private final CardResultService cardResultService;

  // from · to 는 꼭 보내야 합니다. 안 보내거나 모양이 틀리면 400 입니다.
  @Operation(
      summary = "기간 안의 카드 하루치 결과 (실행 · 지연 · 중도 종료 · 미루기 · 실패 · 휴식 · 사후 입력 · 미처리 · 31일까지 · 토큰 필요)")
  @GetMapping("/results")
  public List<CardDayResultResponse> getResults(
      @Parameter(description = "시작 날짜(포함). 예: 2026-10-01", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "끝 날짜(포함). 예: 2026-10-17", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return cardResultService.getResults(from, to);
  }

  // goalId 가 숫자가 아니면 Spring 이 막고 400 으로 답합니다.
  @Operation(summary = "한 목표의 최근 4주 계획 패턴 (과대 · 적정 · 느슨, 5번보다 적으면 null · 토큰 필요)")
  @GetMapping("/plan-pattern")
  public PlanPatternResponse getPlanPattern(
      @Parameter(description = "목표 번호. 예: 12", required = true) @RequestParam Long goalId) {
    return cardResultService.getPlanPattern(goalId);
  }
}
