package com.ithopeschool.projectbe.domain.plan;

import com.ithopeschool.projectbe.domain.plan.dto.PlanParseRequest;
import com.ithopeschool.projectbe.domain.plan.dto.PlanParseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// "말로 계획 세우기" API 의 입구입니다. 요청을 받아 PlanParseService 를 부르기만 합니다. (docs/API.md "말로 계획 세우기")
// 주소는 공부 계획과 같은 /api/v1/study-plans 아래지만, GPT 를 쓰는 기능이라 Controller 를 따로 두었습니다.
// 로그인이 필요합니다.
@Tag(name = "공부 계획")
@RestController
@RequestMapping("/api/v1/study-plans")
@RequiredArgsConstructor
public class PlanParseController {

  private final PlanParseService planParseService;

  // 저장하지 않으므로 201 이 아니라 200 입니다.
  @Operation(summary = "말로 계획 세우기: 문장 → 계획 초안 (저장 안 함 · 200자까지 · AI 하루 한도 · 토큰 필요)")
  @PostMapping("/parse")
  public PlanParseResponse parse(@Valid @RequestBody PlanParseRequest request) {
    return planParseService.parse(request);
  }
}
