package com.ithopeschool.projectbe.domain.onboarding.dto;

import com.ithopeschool.projectbe.domain.onboarding.UserInitialPlan;
import java.time.LocalDate;

// 내 처음 계획을 FE 에 알려 주는 모양입니다. FE 의 InitialPlan 과 똑같습니다.
//   { "createdDate": "2026-10-17", "dailyMinutes": 180, "daysPerWeek": 5 }
// 누구의 계획인지(userId)는 담지 않습니다.
public record InitialPlanResponse(LocalDate createdDate, int dailyMinutes, int daysPerWeek) {

  public static InitialPlanResponse from(UserInitialPlan plan) {
    return new InitialPlanResponse(
        plan.getCreatedDate(), plan.getDailyMinutes(), plan.getDaysPerWeek());
  }
}
