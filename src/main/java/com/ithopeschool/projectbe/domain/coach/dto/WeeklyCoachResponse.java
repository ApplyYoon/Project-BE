package com.ithopeschool.projectbe.domain.coach.dto;

// 주간 코치 문단의 응답 모양입니다. (FE src/lib/weeklyCoach.ts)
//   { "paragraph": "이번 주는 계획의 64% 를 했어. …" }   ← AI 가 쓴 문단
//   { "paragraph": null }                                  ← AI 문단이 없음. FE 가 기본 문장을 보여줍니다.
//
// AI 문단은 "있으면 더 좋은 것" 이라서, 못 만든 경우도 에러가 아니라 200 과 null 로 답합니다.
public record WeeklyCoachResponse(String paragraph) {

  // AI 문단이 없을 때의 응답
  public static WeeklyCoachResponse none() {
    return new WeeklyCoachResponse(null);
  }
}
