package com.ithopeschool.projectbe.domain.experiment;

import com.ithopeschool.projectbe.domain.experiment.dto.ExperimentResponse;
import com.ithopeschool.projectbe.domain.experiment.dto.ExperimentStartRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 자기 실험 API 입니다. 판정은 ExperimentService · ExperimentRule 이 하고, 여기서는 넘기기만 합니다. (이슈 #52 · #53 · #54)
@Tag(name = "자기 실험")
@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
public class ExperimentController {

  private final ExperimentService experimentService;

  @Operation(summary = "자기 실험 시작 (가설 · 전략 하나 · 붙일 카드 1~5장 · 14일 · 진행 중은 2개까지 · 토큰 필요)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ExperimentResponse start(@Valid @RequestBody ExperimentStartRequest request) {
    return experimentService.start(request);
  }

  @Operation(summary = "내 자기 실험 목록 (끝난 실험은 이때 판정 · 이행률 · 근거 실행률 포함 · 토큰 필요)")
  @GetMapping
  public List<ExperimentResponse> list() {
    return experimentService.list();
  }
}
