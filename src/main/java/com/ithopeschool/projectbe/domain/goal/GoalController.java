package com.ithopeschool.projectbe.domain.goal;

import com.ithopeschool.projectbe.domain.goal.dto.GoalCreateRequest;
import com.ithopeschool.projectbe.domain.goal.dto.GoalResponse;
import com.ithopeschool.projectbe.domain.goal.dto.GoalUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// "목표(WOOP)" API 의 입구입니다. 네 주소 모두 로그인이 필요합니다. (docs/API.md "목표")
// 지우는 주소는 없습니다. 그만두면 "포기" 로 상태를 바꿉니다.
@Tag(name = "목표")
@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

  private final GoalService goalService;

  @Operation(summary = "내 목표 목록 (만든 순서 · 진행 · 완료 · 포기 모두)")
  @GetMapping
  public List<GoalResponse> getGoals() {
    return goalService.getGoals();
  }

  @Operation(summary = "목표 만들기 (진행 중 3개면 409 · D-day 는 내일 이후)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public GoalResponse create(@Valid @RequestBody GoalCreateRequest request) {
    return goalService.create(request);
  }

  @Operation(summary = "목표 고치기 (목표값을 바꾸면 이유 필수 · 이전 값은 이력에 · 포기하면 이유 필수)")
  @PatchMapping("/{id}")
  public GoalResponse update(@PathVariable Long id, @Valid @RequestBody GoalUpdateRequest request) {
    return goalService.update(id, request);
  }

  @Operation(summary = "목표값을 고친 이력 (오래된 것부터)")
  @GetMapping("/{id}/history")
  public List<GoalResponse.Snapshot> getHistory(@PathVariable Long id) {
    return goalService.getHistory(id);
  }
}
