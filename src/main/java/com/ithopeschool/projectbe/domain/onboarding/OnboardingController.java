package com.ithopeschool.projectbe.domain.onboarding;

import com.ithopeschool.projectbe.domain.onboarding.dto.InitialPlanRequest;
import com.ithopeschool.projectbe.domain.onboarding.dto.InitialPlanResponse;
import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingResponse;
import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingSaveRequest;
import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// "온보딩" API 의 입구입니다. 여섯 주소 모두 로그인이 필요합니다. (docs/API.md "온보딩")
// 주소가 복수형이 아닌 이유: 한 사람에게 "지금 내 설문" 은 하나뿐이라서, 이슈 #21 에 적힌 /onboarding 을 그대로 씁니다.
@Tag(name = "온보딩")
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

  private final OnboardingService onboardingService;
  private final InitialPlanService initialPlanService;

  @Operation(summary = "내 설문 답 (지금 답 + 처음 답한 자기 인식 · 아직 안 했으면 404)")
  @GetMapping
  public OnboardingResponse get() {
    return onboardingService.get();
  }

  @Operation(summary = "설문 답 저장 (학년·시험·시험 날짜·하루 공부 가능 시간은 필수 · 저장할 때마다 새로 쌓임)")
  @PutMapping
  public OnboardingResponse save(@Valid @RequestBody OnboardingSaveRequest request) {
    return onboardingService.save(request);
  }

  @Operation(summary = "설문 답 일부만 고치기 (보낸 항목만 바뀜 · 설문을 아직 안 했으면 404)")
  @PatchMapping
  public OnboardingResponse update(@Valid @RequestBody OnboardingUpdateRequest request) {
    return onboardingService.update(request);
  }

  @Operation(summary = "내 처음 계획 (아직 안 세웠으면 404)")
  @GetMapping("/initial-plan")
  public InitialPlanResponse getInitialPlan() {
    return initialPlanService.get();
  }

  @Operation(summary = "처음 계획 세우기 (한 번만 · 이미 있으면 409)")
  @PostMapping("/initial-plan")
  @ResponseStatus(HttpStatus.CREATED)
  public InitialPlanResponse createInitialPlan(@Valid @RequestBody InitialPlanRequest request) {
    return initialPlanService.create(request);
  }

  @Operation(summary = "처음 계획 다시 세우기 (세운 날짜가 오늘로 바뀜 · 아직 안 세웠으면 404)")
  @PutMapping("/initial-plan")
  public InitialPlanResponse remakeInitialPlan(@Valid @RequestBody InitialPlanRequest request) {
    return initialPlanService.remake(request);
  }
}
