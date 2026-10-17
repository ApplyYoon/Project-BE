package com.ithopeschool.projectbe.domain.coach;

import com.ithopeschool.projectbe.domain.coach.dto.CoachCommentRequest;
import com.ithopeschool.projectbe.domain.coach.dto.CoachParagraphResponse;
import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// AI 코치 API 의 입구입니다. 요청을 받아 검사(@Valid)하고 CoachService 를 부르기만 합니다. (docs/API.md "주간 코치 문단")
// 주소가 /coaches 가 아니라 /coach 인 이유: 목록이 있는 "무엇" 이 아니라 "코치에게 물어보기" 라는 동작이라서입니다.
@Tag(name = "AI 코치")
@RestController
@RequestMapping("/api/v1/coach")
@RequiredArgsConstructor
public class CoachController {

  private final CoachService coachService;

  // 만드는 것이 아니라 "물어보는" 것이라 201 이 아니라 200 으로 답합니다.
  @Operation(summary = "주간 코치 문단 받기 (숫자를 주면 AI 가 말로 풀어 줌 · 토큰 필요 · 배포 서버에서만 AI 가 켜짐)")
  @PostMapping("/weekly")
  public CoachParagraphResponse weekly(@Valid @RequestBody WeeklyCoachRequest request) {
    return coachService.weeklyParagraph(request);
  }

  @Operation(summary = "어제 한 줄 받기 (홈 화면 · 숫자를 주면 AI 가 1~2문장으로 풀어 줌 · 토큰 필요)")
  @PostMapping("/daily")
  public CoachParagraphResponse daily(@Valid @RequestBody CoachCommentRequest request) {
    return coachService.dailyParagraph(request);
  }

  @Operation(summary = "월간 코치 문단 받기 (최근 4주 · 숫자를 주면 AI 가 말로 풀어 줌 · 토큰 필요)")
  @PostMapping("/monthly")
  public CoachParagraphResponse monthly(@Valid @RequestBody CoachCommentRequest request) {
    return coachService.monthlyParagraph(request);
  }
}
