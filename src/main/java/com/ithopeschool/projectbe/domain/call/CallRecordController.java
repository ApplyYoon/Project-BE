package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

  // 이미 만든 통화 기록을 채우는 것이라 201 이 아니라 200 으로 답합니다.
  @Operation(summary = "AI 통화 마무리하기 (자막 → 요약·생각 습관·약속 · 자막은 저장 안 함 · 배포 서버에서만 정리됨)")
  @PostMapping("/{id}/finish")
  public CallFinishResponse finish(
      @PathVariable Long id, @Valid @RequestBody CallFinishRequest request) {
    return callRecordService.finish(id, request);
  }
}
