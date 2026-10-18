package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionResponse;
import com.ithopeschool.projectbe.domain.call.dto.CheckCallResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// AI 통화 API 의 입구입니다. 요청을 받아 검사(@Valid)하고 CallService 를 부르기만 합니다. (docs/API.md "AI 통화")
@Tag(name = "AI 통화")
@RestController
@RequestMapping("/api/v1/realtime")
@RequiredArgsConstructor
public class CallController {

  private final CallService callService;
  private final CheckCallService checkCallService;

  // 통화 기록을 만드는 것이 아니라 "임시 키를 받아 가는" 것이라 201 이 아니라 200 으로 답합니다.
  @Operation(summary = "AI 통화 시작하기 (임시 키 받기 · 토큰 필요 · 배포 서버에서만 켜짐)")
  @PostMapping("/session")
  public CallSessionResponse startSession(@Valid @RequestBody CallSessionRequest request) {
    return callService.startSession(request);
  }

  // 확인 통화 문이 열렸는지 봅니다. 오늘 공부를 하나 끝내야 열립니다. (2026-10-18, CheckCall)
  @Operation(summary = "확인 통화가 열렸는지 보기 (오늘 끝낸 공부 · 토큰 필요)")
  @GetMapping("/check-call")
  public CheckCallResponse checkCall() {
    return checkCallService.check();
  }
}
