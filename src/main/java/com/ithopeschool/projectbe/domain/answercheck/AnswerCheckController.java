package com.ithopeschool.projectbe.domain.answercheck;

import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckCreateRequest;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckItemUpdateRequest;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// "사진 채점" API 의 입구입니다. 요청을 받아 AnswerCheckService 를 부르기만 합니다. 로그인이 필요합니다.
// 폰이 사진을 올리고(POST), PC 가 그날 기록을 보고(GET), 학생이 문제별로 판정을 고칩니다(PATCH).
@Tag(name = "사진 채점")
@RestController
@RequestMapping("/api/v1/answer-checks")
@RequiredArgsConstructor
public class AnswerCheckController {

  private final AnswerCheckService answerCheckService;

  // 채점하지 못해도(AI 꺼짐 · 문제 사진이 아님) 에러가 아니라 200 에 graded:false 입니다.
  @Operation(summary = "문제집 사진 채점 (사진은 저장하지 않음 · 결과만 저장 · 못 하면 graded:false · 토큰 필요)")
  @PostMapping
  public AnswerCheckResponse create(@Valid @RequestBody AnswerCheckCreateRequest request) {
    return answerCheckService.create(request);
  }

  @Operation(summary = "그날의 내 채점 기록 목록 (최근에 올린 것부터 · 토큰 필요)")
  @GetMapping
  public List<AnswerCheckResponse> getChecks(
      @Parameter(description = "공부한 날짜. 예: 2026-10-24", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return answerCheckService.getChecks(date);
  }

  @Operation(summary = "문제 하나의 판정 고치기 (AI 의 처음 판정은 남김 · 고친 기록 전체를 돌려줌 · 토큰 필요)")
  @PatchMapping("/{id}/items/{itemId}")
  public AnswerCheckResponse updateItem(
      @Parameter(description = "채점 기록 번호", required = true) @PathVariable Long id,
      @Parameter(description = "문제 번호(items 의 id)", required = true) @PathVariable Long itemId,
      @Valid @RequestBody AnswerCheckItemUpdateRequest request) {
    return answerCheckService.updateItem(id, itemId, request);
  }
}
