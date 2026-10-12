package com.ithopeschool.projectbe.domain.plan;

import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanResponse;
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

// 공부 계획 API 의 입구입니다. 요청을 받아 StudyPlanService 를 부르기만 합니다. (docs/API.md "공부 계획")
@Tag(name = "공부 계획")
@RestController
@RequestMapping("/api/v1/study-plans")
@RequiredArgsConstructor
public class StudyPlanController {

  private final StudyPlanService studyPlanService;

  // date 는 꼭 보내야 합니다. 안 보내거나 모양이 틀리면(2026/10/05) Spring 이 막고,
  // GlobalExceptionHandler 가 400 VALIDATION_ERROR 로 바꿔 답합니다.
  // @DateTimeFormat(ISO.DATE): "2026-10-05" 모양만 날짜로 받겠다는 표시입니다.
  @Operation(summary = "그날의 내 공부 계획 목록 (만든 순서 · 토큰 필요)")
  @GetMapping
  public List<StudyPlanResponse> getPlans(
      @Parameter(description = "계획한 날짜. 예: 2026-10-05", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return studyPlanService.getPlans(date);
  }
}
