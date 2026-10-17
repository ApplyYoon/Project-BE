package com.ithopeschool.projectbe.domain.reflection;

import com.ithopeschool.projectbe.domain.reflection.dto.ReflectionCurrentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// "성찰일기 처방" API 의 입구입니다. 로그인이 필요합니다. (docs/API.md "성찰일기 처방")
@Tag(name = "성찰일기 처방")
@RestController
@RequestMapping("/api/v1/reflections")
@RequiredArgsConstructor
public class ReflectionController {

  private final ReflectionService reflectionService;

  @Operation(summary = "지금 내 성찰일기 처방 (없으면 prescription 이 null · 처음 물어본 날 정해지고 5일 동안 같음)")
  @GetMapping("/current")
  public ReflectionCurrentResponse getCurrent() {
    return reflectionService.getCurrent();
  }
}
