package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 공부 기록 API 의 입구입니다. 요청을 받아 StudySessionService 를 부르기만 합니다. (docs/API.md "공부 기록")
// 로그인이 필요합니다.
@Tag(name = "공부 기록")
@RestController
@RequestMapping("/api/v1/study-sessions")
@RequiredArgsConstructor
public class StudySessionController {

  private final StudySessionService studySessionService;

  @Operation(summary = "타이머 기록 저장 (순공 1분 이상 · 미래·겹치는 시간은 거절 · 노는 기록은 과목 없음 · 토큰 필요)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public StudySessionResponse create(@Valid @RequestBody StudySessionCreateRequest request) {
    return studySessionService.create(request);
  }
}
