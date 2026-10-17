package com.ithopeschool.projectbe.domain.dailysignal;

import com.ithopeschool.projectbe.domain.dailysignal.dto.DailySignalResponse;
import com.ithopeschool.projectbe.domain.dailysignal.dto.DailySignalSaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// "하루 신호" API 의 입구입니다. 로그인이 필요합니다. (docs/API.md "하루 신호")
@Tag(name = "하루 신호 (휴대폰·수면·걸음)")
@RestController
@RequestMapping("/api/v1/daily-signals")
@RequiredArgsConstructor
public class DailySignalController {

  private final DailySignalService dailySignalService;

  // 새로 만들 때도, 덮어쓸 때도 200 을 돌려줍니다. (FE 는 어느 쪽인지 신경 쓰지 않아도 됩니다)
  @Operation(summary = "하루치 휴대폰·수면·걸음 데이터 보내기 (같은 날 다시 보내면 덮어씀 · 날짜 말고는 다 비어도 됨)")
  @PostMapping
  public DailySignalResponse save(@Valid @RequestBody DailySignalSaveRequest request) {
    return dailySignalService.save(request);
  }
}
