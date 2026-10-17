package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.BiasHabitResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallRecordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// AI 통화 기록 API 의 입구입니다. 로그인이 필요합니다. (docs/API.md "AI 통화 마무리")
// 통화를 시작하는 주소(/api/v1/realtime/session)는 CallController 에 있습니다.
@Tag(name = "AI 통화 기록")
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class CallRecordController {

  private final CallRecordService callRecordService;

  @Operation(summary = "내 최근 30일 통화 기록 (최근 것부터 · 마무리한 통화만 · 토큰 필요)")
  @GetMapping
  public List<CallRecordResponse> getRecentCalls() {
    return callRecordService.getRecentCalls();
  }

  @Operation(summary = "자주 보이는 내 생각 습관 (최근 14일 중 서로 다른 3일 이상 · 토큰 필요)")
  @GetMapping("/habits")
  public List<BiasHabitResponse> getHabits() {
    return callRecordService.getHabits();
  }

  // 이미 만든 통화 기록을 채우는 것이라 201 이 아니라 200 으로 답합니다.
  @Operation(summary = "AI 통화 마무리하기 (자막 → 요약·생각 습관·약속 · 자막은 저장 안 함 · 배포 서버에서만 정리됨)")
  @PostMapping("/{id}/finish")
  public CallFinishResponse finish(
      @PathVariable Long id, @Valid @RequestBody CallFinishRequest request) {
    return callRecordService.finish(id, request);
  }
}
