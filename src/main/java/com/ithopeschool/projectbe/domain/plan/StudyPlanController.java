package com.ithopeschool.projectbe.domain.plan;

import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanBatchCreateRequest;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanCreateRequest;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanResponse;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanUpdateRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 공부 계획 API 의 입구입니다. 요청을 받아 StudyPlanService 를 부르기만 합니다. (docs/API.md "공부 계획")
// 다섯 주소 모두 로그인이 필요합니다.
@Tag(name = "공부 계획")
@RestController
@RequestMapping("/api/v1/study-plans")
@RequiredArgsConstructor
public class StudyPlanController {

  private final StudyPlanService studyPlanService;

  // 두 가지로 부릅니다. date 하나(그날 하루) 또는 from · to 둘(기간, 이슈 #250). 섞어 보내거나 하나만 보내면 400 입니다.
  // 날짜 모양이 틀리면(2026/10/05) Spring 이 막고, GlobalExceptionHandler 가 400 VALIDATION_ERROR 로 바꿔 답합니다.
  // @DateTimeFormat(ISO.DATE): "2026-10-05" 모양만 날짜로 받겠다는 표시입니다.
  @Operation(
      summary =
          "내 공부 계획 목록: date 로 그날 하루, 또는 from · to 로 기간 (기간은 62일까지 · 날짜순 → 시작 시각 순 → 시간 미정은 만든 순 · 토큰 필요)")
  @GetMapping
  public List<StudyPlanResponse> getPlans(
      @Parameter(description = "계획한 날짜 (그날 하루). 예: 2026-10-05")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date,
      @Parameter(description = "기간의 첫날 (포함). to 와 함께 보냅니다. 예: 2026-09-01")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "기간의 끝날 (포함). from 과 함께 보냅니다. 예: 2026-10-17")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    if (date != null && from == null && to == null) {
      return studyPlanService.getPlans(date);
    }
    if (date == null && from != null && to != null) {
      return studyPlanService.getPlansBetween(from, to);
    }
    throw new ApiException(ErrorCode.VALIDATION_ERROR, "date 하나, 또는 from 과 to 를 함께 보내 주세요");
  }

  @Operation(summary = "계획 추가 (목표 1~600분 · 공부 계획은 내 과목 필수 · 노는 계획은 과목 없음)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public StudyPlanResponse create(@Valid @RequestBody StudyPlanCreateRequest request) {
    return studyPlanService.create(request);
  }

  @Operation(summary = "계획 여러 개 한 번에 추가 (50개까지 · 하나라도 틀리면 전부 저장 안 함)")
  @PostMapping("/batch")
  @ResponseStatus(HttpStatus.CREATED)
  public List<StudyPlanResponse> createAll(
      @Valid @RequestBody StudyPlanBatchCreateRequest request) {
    return studyPlanService.createAll(request);
  }

  @Operation(summary = "계획 바꾸기 · 완료 체크 (바꿀 것만 보냄 · startTime 을 \"\" 로 보내면 시간 미정)")
  @PatchMapping("/{id}")
  public StudyPlanResponse update(
      @PathVariable Long id, @Valid @RequestBody StudyPlanUpdateRequest request) {
    return studyPlanService.update(id, request);
  }

  @Operation(summary = "계획 삭제")
  @DeleteMapping("/{id}")
  // 돌려줄 내용이 없어서 204 로 답합니다. (docs/API.md 4장)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    studyPlanService.delete(id);
  }
}
