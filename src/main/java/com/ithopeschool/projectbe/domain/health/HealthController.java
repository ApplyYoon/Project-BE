package com.ithopeschool.projectbe.domain.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 서버가 켜져 있는지 확인하는 API 입니다. GET /api/v1/health → {"status":"ok"}
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  @GetMapping
  public HealthResponse health() {
    return new HealthResponse("ok");
  }
}
