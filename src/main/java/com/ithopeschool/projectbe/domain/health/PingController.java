package com.ithopeschool.projectbe.domain.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "서버 상태")
@RestController
public class PingController {

  // 외부 스케줄러가 서버만 깨워 둡니다. DB 연결이나 조회는 하지 않습니다.
  @Operation(summary = "DB 조회 없이 서버가 살아 있는지 확인")
  @GetMapping("/api/v1/ping")
  public ResponseEntity<PingResponse> ping() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PingResponse("ok"));
  }

  public record PingResponse(String status) {}
}
