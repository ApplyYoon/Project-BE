package com.ithopeschool.projectbe.domain.focus;

import com.ithopeschool.projectbe.domain.focus.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "앱 잠금 해제 기록")
@RestController
@RequestMapping("/api/v1/lock-events")
@RequiredArgsConstructor
public class LockEventController {
  private final LockEventService service;

  @Operation(summary = "잠금 해제 시도 기록 저장")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LockEventResponse create(@Valid @RequestBody LockEventRequest request) {
    return service.create(request);
  }

  @Operation(summary = "내 해제 기록을 시간대와 앱별로 집계")
  @GetMapping("/stats")
  public LockEventStats stats(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
    return service.stats(startDate, endDate);
  }
}
