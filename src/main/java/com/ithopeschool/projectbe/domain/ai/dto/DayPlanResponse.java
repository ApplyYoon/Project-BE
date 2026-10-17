package com.ithopeschool.projectbe.domain.ai.dto;

import java.time.LocalDate;
import java.util.List;

// AI 하루 계획의 결과입니다. **저장하지 않은 초안**입니다.
//   { "date": "2026-10-18", "blockMinutes": 25, "totalMinutes": 150,
//     "plans": [ { "date": "2026-10-18", "subjectId": "3", "content": "기출 1회분 풀기", "targetMinutes":
// 25,
//                  "startTime": null, "kind": "study" }, … ],
//     "evidence": ["최근 14일 공부 기록 12번의 한 번 길이 가운데값이 24분이라 25분씩 나눴어요", …] }
// - plans 의 한 줄은 계획 추가 요청과 같은 모양입니다. 학생이 고친 뒤 FE 가 그대로 /study-plans/batch 로 저장합니다.
// - evidence: 왜 이렇게 짰는지. 숫자는 모두 내 기록으로 코드가 정했고, 그 근거입니다.
// - plans 가 빈 목록이면 짤 것이 없는 것입니다. (과목이 없음 · 그날 이미 충분히 계획함) 이유는 evidence 에 있습니다.
public record DayPlanResponse(
    LocalDate date, int blockMinutes, int totalMinutes, List<Plan> plans, List<String> evidence) {

  // 계획 초안 한 줄 (계획 추가 요청과 같은 모양)
  public record Plan(
      LocalDate date,
      String subjectId,
      String content,
      int targetMinutes,
      String startTime,
      String kind) {}
}
