package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.session.dto.PlanActualResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudyTotalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 공부 기록 API 의 입구입니다. 요청을 받아 StudySessionService 를 부르기만 합니다. (docs/API.md "공부 기록")
// 모든 주소가 로그인이 필요합니다.
@Tag(name = "공부 기록")
@RestController
@RequestMapping("/api/v1/study-sessions")
@RequiredArgsConstructor
public class StudySessionController {

  private final StudySessionService studySessionService;

  // from · to 는 꼭 보내야 합니다. 안 보내거나 모양이 틀리면(2026/10/01) Spring 이 막고,
  // GlobalExceptionHandler 가 400 VALIDATION_ERROR 로 바꿔 답합니다.
  // @DateTimeFormat(ISO.DATE): "2026-10-01" 모양만 날짜로 받겠다는 표시입니다.
  @Operation(summary = "기간 안의 내 공부 기록 목록 (시작 시각 순 · from~to 포함 · 하루는 새벽 4시 기준 · 31일까지 · 토큰 필요)")
  @GetMapping
  public List<StudySessionResponse> getSessions(
      @Parameter(description = "시작 날짜(포함). 예: 2026-10-01", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "끝 날짜(포함). 예: 2026-10-07", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return studySessionService.getSessions(from, to);
  }

  @Operation(summary = "오늘 순공시간(분)과 과목별 합계 (일시정지 뺀 시간 · 노는 기록 제외 · 하루는 새벽 4시 기준 · 토큰 필요)")
  @GetMapping("/today/total")
  public StudyTotalResponse getTodayTotal() {
    return studySessionService.getTodayTotal();
  }

  @Operation(summary = "그날 계획마다 실제 시간 / 진행률 (그 계획으로 켠 기록의 순공 합 · 노는 계획 포함 · 토큰 필요)")
  @GetMapping("/by-plan")
  public List<PlanActualResponse> getPlanActuals(
      @Parameter(description = "계획한 날짜. 예: 2026-10-05", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return studySessionService.getPlanActuals(date);
  }

  @Operation(summary = "타이머 기록 저장 (순공 1분 이상 · 미래·겹치는 시간은 거절 · 노는 기록은 과목 없음 · 토큰 필요)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public StudySessionResponse create(@Valid @RequestBody StudySessionCreateRequest request) {
    return studySessionService.create(request);
  }
}
