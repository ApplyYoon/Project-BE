package com.ithopeschool.projectbe.domain.routine;

import com.ithopeschool.projectbe.domain.routine.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/routines")
@Tag(name = "계획 루틴")
@RequiredArgsConstructor
public class RoutineController {
  private final RoutineService service;

  @GetMapping
  @Operation(summary = "내 루틴 목록")
  public List<RoutineResponse> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "루틴 추가")
  public RoutineResponse create(@Valid @RequestBody RoutineRequest request) {
    return service.create(request);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "루틴의 보낸 칸 수정")
  public RoutineResponse update(@PathVariable Long id, @Valid @RequestBody RoutineRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "루틴 삭제 (이미 만든 계획은 유지)")
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
