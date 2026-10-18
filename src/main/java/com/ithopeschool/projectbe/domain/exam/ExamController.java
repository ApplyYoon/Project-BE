package com.ithopeschool.projectbe.domain.exam;

import com.ithopeschool.projectbe.domain.exam.dto.ExamCreateRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamGoalRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamResponse;
import com.ithopeschool.projectbe.domain.exam.dto.ExamResultRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 시험 점수 API 의 입구입니다. 요청을 받아 ExamService 를 부르기만 합니다. (docs/API.md "시험 점수")
// 세 주소 모두 로그인이 필요합니다.
@Tag(name = "시험 점수")
@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
public class ExamController {

  private final ExamService examService;

  @Operation(summary = "내 시험 목록 (시험 날짜가 늦은 것부터 · goalId 를 주면 그 목표에 이어진 시험만 · 토큰 필요)")
  @GetMapping
  public List<ExamResponse> getExams(@RequestParam(required = false) Long goalId) {
    return goalId == null ? examService.getExams() : examService.getExams(goalId);
  }

  @Operation(summary = "시험 등록 (종류 mock·school·quiz·etc · 과목별 예상 점수 0~100 · 같은 과목은 한 번만 · 토큰 필요)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ExamResponse create(@Valid @RequestBody ExamCreateRequest request) {
    return examService.create(request);
  }

  @Operation(summary = "시험을 목표에 잇기 · 떼기 (점수형 목표만 · goalId: null 이면 떼기 · 토큰 필요)")
  @PatchMapping("/{id}/goal")
  public ExamResponse linkGoal(@PathVariable Long id, @Valid @RequestBody ExamGoalRequest request) {
    return examService.linkGoal(id, request);
  }

  @Operation(summary = "실제 점수 입력 (시험 날짜가 지난 뒤부터 · 보낸 과목만 바뀜 · 토큰 필요)")
  @PatchMapping("/{id}")
  public ExamResponse enterActualScores(
      @PathVariable Long id, @Valid @RequestBody ExamResultRequest request) {
    return examService.enterActualScores(id, request);
  }
}
