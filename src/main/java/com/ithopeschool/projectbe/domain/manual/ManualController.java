package com.ithopeschool.projectbe.domain.manual;

import com.ithopeschool.projectbe.domain.manual.dto.ManualItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 나 사용설명서 API 입니다. 항목을 모으는 일은 ManualService 가 하고, 여기서는 넘기기만 합니다. (이슈 #46)
@Tag(name = "나 사용설명서")
@RestController
@RequestMapping("/api/v1/manual")
@RequiredArgsConstructor
public class ManualController {

  private final ManualService manualService;

  @Operation(summary = "내 사용설명서 목록 (효과가 확인된 실험 · 확실성 높은 인사이트만 · 최근 확인된 것부터 · 토큰 필요)")
  @GetMapping
  public List<ManualItemResponse> list() {
    return manualService.list();
  }

  @Operation(summary = "사용설명서 항목 숨기기 (이미 숨겼어도 204 · 내 목록에 없는 항목은 404 · 토큰 필요)")
  @PutMapping("/hidden-items/{itemId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void hide(@PathVariable String itemId) {
    manualService.hide(itemId);
  }
}
