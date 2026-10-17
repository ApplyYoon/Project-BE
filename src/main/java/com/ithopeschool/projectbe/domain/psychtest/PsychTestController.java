package com.ithopeschool.projectbe.domain.psychtest;

import com.ithopeschool.projectbe.domain.psychtest.dto.PsychTestResponse;
import com.ithopeschool.projectbe.domain.psychtest.dto.PsychTestSaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 온보딩 심리검사 API 의 입구입니다. 요청을 받아 PsychTestService 를 부르기만 합니다. (이슈 #219, docs/API.md "심리검사")
// 두 주소 모두 로그인이 필요합니다.
@Tag(name = "심리검사")
@RestController
@RequestMapping("/api/v1/psych-tests")
@RequiredArgsConstructor
public class PsychTestController {

  private final PsychTestService psychTestService;

  @Operation(summary = "심리검사 결과 저장 (문장 14개의 답 1~7 · 척도 7개 점수를 서버가 계산 · 다시 하면 새로 쌓임 · 토큰 필요)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PsychTestResponse save(@Valid @RequestBody PsychTestSaveRequest request) {
    return psychTestService.save(request);
  }

  @Operation(summary = "내 가장 최근 심리검사 결과 (안 했으면 404 · 토큰 필요)")
  @GetMapping("/latest")
  public PsychTestResponse getLatest() {
    return psychTestService.getLatest();
  }
}
