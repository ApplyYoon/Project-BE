package com.ithopeschool.projectbe.domain.answercheck;

import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckCreateRequest;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckItemUpdateRequest;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
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
// 폰이 사진을 올리고(POST), PC 가 그날 기록을 보고(GET, 주간 리포트는 기간으로), 학생이 문제별로 판정을 고칩니다(PATCH).
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

  // 두 가지로 부릅니다. date 하나(그날 하루, PC 화면) 또는 from · to 둘(기간, 주간 리포트). 섞어 보내거나 하나만 보내면 400 입니다.
  // 날짜 모양이 틀리면(2026/10/24) Spring 이 막고, GlobalExceptionHandler 가 400 VALIDATION_ERROR 로 바꿔 답합니다.
  @Operation(
      summary =
          "내 채점 기록 목록: date 로 그날 하루, 또는 from · to 로 기간 (기간은 31일까지 · 최근 날짜부터 → 같은 날은 최근에 올린 것부터 · 토큰 필요)")
  @GetMapping
  public List<AnswerCheckResponse> getChecks(
      @Parameter(description = "공부한 날짜 (그날 하루). 예: 2026-10-24")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date,
      @Parameter(description = "기간의 첫날 (포함). to 와 함께 보냅니다. 예: 2026-10-18")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "기간의 끝날 (포함). from 과 함께 보냅니다. 예: 2026-10-24")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    if (date != null && from == null && to == null) {
      return answerCheckService.getChecks(date);
    }
    if (date == null && from != null && to != null) {
      return answerCheckService.getChecksBetween(from, to);
    }
    throw new ApiException(ErrorCode.VALIDATION_ERROR, "date 하나, 또는 from 과 to 를 함께 보내 주세요");
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
