package com.ithopeschool.projectbe.domain.coach.dto;

import java.util.List;

// AI 코치 문단의 응답 모양입니다. 어제 한 줄·주간·월간이 모두 이 모양을 씁니다. (FE src/lib/weeklyCoach.ts 등)
//   { "paragraph": "이번 주는 계획의 64% 를 했어. …",          ← AI 가 쓴 문단
//     "evidence": [{ "label": "계획 대비 실제", "value": "64%", "basis": "계획 12개" }, …] }
//   { "paragraph": null, "evidence": [ … ] }                  ← AI 문단이 없음. FE 가 기본 문장을 보여줍니다.
//
// AI 문단은 "있으면 더 좋은 것" 이라서, 못 만든 경우도 에러가 아니라 200 과 null 로 답합니다.
// evidence: 근거 숫자 목록. 이번에 받은 숫자 그대로이고, AI 는 이 숫자만 가지고 문단을 씁니다.
//           FE 의 "근거 숫자 보기" 에 씁니다. (이슈 #87) 문단이 없어도 들어 있습니다.
public record CoachParagraphResponse(String paragraph, List<WeeklyCoachRequest.Number> evidence) {

  // AI 문단이 없을 때의 응답
  public static CoachParagraphResponse none(List<WeeklyCoachRequest.Number> evidence) {
    return new CoachParagraphResponse(null, evidence);
  }
}
