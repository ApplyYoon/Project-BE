package com.ithopeschool.projectbe.domain.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 서버가 켜져 있는지 확인하는 API 입니다. GET /api/v1/health → {"status":"ok"}
// @Tag 와 @Operation 은 Swagger 화면에 보이는 한국어 설명입니다.
@Tag(name = "상태 확인")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  @Operation(summary = "서버가 켜져 있는지 확인")
  @GetMapping
  public HealthResponse health() {
    return new HealthResponse("ok");
  }
}
