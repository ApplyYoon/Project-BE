package com.ithopeschool.projectbe.domain.variant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 변형 문제 기능의 설정값 모음입니다. (docs/VARIANT-SPEC.md 9장)
// 값은 application.yml 의 app.variant 아래에 있고, 적지 않으면 여기 적힌 기본값을 씁니다.
// 숫자만 바꾸면 되는 규칙(하루 문항 수, 레벨을 내리는 연속 오답 수 등)은 코드 대신 여기서 바꿉니다.
//
// 테스트에서는 defaults() 로 기본값 그대로 만들어 씁니다.
@Component
public record VariantSettings(
    // 변형 하나를 만들 때 AI 에게 다시 시키는 최대 횟수 (실패 사유를 붙여 다시 만듭니다)
    @Value("${app.variant.max-generation-attempts:3}") int maxGenerationAttempts,
    // G7 독립 풀이 검사를 켤지 (정답을 숨기고 AI 에게 한 번 더 풀게 함)
    @Value("${app.variant.enable-independent-solve-check:false}")
        boolean enableIndependentSolveCheck,
    // 샌드박스(따로 띄운 계산 프로그램)가 이 시간(초) 안에 안 끝나면 강제로 끕니다
    @Value("${app.variant.sandbox-timeout-sec:10}") int sandboxTimeoutSec,
    // 샌드박스가 쓸 수 있는 최대 메모리(MB)
    @Value("${app.variant.sandbox-memory-mb:256}") int sandboxMemoryMb,
    // 하루에 내는 일일 과제 최대 수
    @Value("${app.variant.daily-max-items:10}") int dailyMaxItems,
    // L0 에서 이만큼 연속으로 틀리면 L-1(비계)로 내립니다
    @Value("${app.variant.l0-wrong-streak-to-drop:2}") int l0WrongStreakToDrop,
    // L-1 에서 이만큼 연속으로 틀리면 "주의" 표시를 붙입니다
    @Value("${app.variant.floor-wrong-streak-to-flag:3}") int floorWrongStreakToFlag,
    // 주간 (A): 맞은 문제에서 고르는 유형 수
    @Value("${app.variant.weekly-correct-types:5}") int weeklyCorrectTypes,
    // 주간 (B): 졸업한 오답에서 고르는 최대 수
    @Value("${app.variant.weekly-wrong-max:10}") int weeklyWrongMax,
    // 주간 세트의 마감(만든 날 + 며칠)
    @Value("${app.variant.weekly-due-days:7}") int weeklyDueDays,
    // 분류기의 자신감이 이보다 낮으면 사람이 확인하도록(NEEDS_REVIEW) 둡니다
    @Value("${app.variant.classify-min-confidence:0.6}") double classifyMinConfidence,
    // 예약 작업 한 번이 일하는 최대 시간(초). 부르는 쪽(cron-job.org)이 30초까지만 기다려서 그보다 짧게 둡니다
    @Value("${app.variant.job-budget-sec:20}") int jobBudgetSec) {

  // 기본값 그대로의 설정 (테스트용)
  public static VariantSettings defaults() {
    return new VariantSettings(3, false, 10, 256, 10, 2, 3, 5, 10, 7, 0.6, 20);
  }
}
