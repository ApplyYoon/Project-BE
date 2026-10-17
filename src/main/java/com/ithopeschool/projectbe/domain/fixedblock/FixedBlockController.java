package com.ithopeschool.projectbe.domain.fixedblock;

import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockCreateRequest;
import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockResponse;
import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// "고정 일정(학교 · 학원)" API 의 입구입니다. 네 주소 모두 로그인이 필요합니다. (docs/API.md "고정 일정")
@Tag(name = "고정 일정")
@RestController
@RequestMapping("/api/v1/fixed-blocks")
@RequiredArgsConstructor
public class FixedBlockController {

  private final FixedBlockService fixedBlockService;

  @Operation(summary = "내 고정 일정 목록 (만든 순서)")
  @GetMapping
  public List<FixedBlockResponse> getBlocks() {
    return fixedBlockService.getBlocks();
  }

  @Operation(summary = "고정 일정 만들기 (이름 20자까지 · 요일 0~6 하나 이상 · 끝은 시작보다 뒤)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FixedBlockResponse create(@Valid @RequestBody FixedBlockCreateRequest request) {
    return fixedBlockService.create(request);
  }

  @Operation(summary = "고정 일정 바꾸기 (바꿀 것만 보냄)")
  @PatchMapping("/{id}")
  public FixedBlockResponse update(
      @PathVariable Long id, @Valid @RequestBody FixedBlockUpdateRequest request) {
    return fixedBlockService.update(id, request);
  }

  @Operation(summary = "고정 일정 지우기")
  @DeleteMapping("/{id}")
  // 돌려줄 내용이 없어서 204 로 답합니다. (docs/API.md 4장)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    fixedBlockService.delete(id);
  }
}
