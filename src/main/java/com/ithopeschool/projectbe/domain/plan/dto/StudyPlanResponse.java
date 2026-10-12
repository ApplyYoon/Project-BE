package com.ithopeschool.projectbe.domain.plan.dto;

import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import java.time.LocalDate;

// 계획 하나를 FE 에 알려 주는 모양입니다. FE 의 StudyPlan 과 같은 이름을 씁니다.
//   { "id": "7", "date": "2026-10-05", "subjectId": "3", "content": "수학 문제집 3단원 풀기",
//     "targetMinutes": 90, "done": false, "startTime": null, "kind": "study", "routineId": null }
// - id, subjectId 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - 누구의 계획인지(userId)는 담지 않습니다.
//
// startTime, kind, routineId 는 아직 study_plans 테이블에 칸이 없습니다. (Project#12 에서 정한 새 항목)
// 지금은 계획을 만드는 API 도 루틴도 없어서 모든 계획이 "시간 미정 · 공부 · 루틴 아님" 이므로 그 값을 넣어 보냅니다.
// 테이블에 칸이 생기면 여기서 엔티티의 값을 꺼내 쓰도록 바꿉니다.
public record StudyPlanResponse(
    String id,
    LocalDate date,
    String subjectId,
    String content,
    int targetMinutes,
    boolean done,
    String startTime,
    String kind,
    String routineId) {

  // FE 의 PlanKind 는 소문자('study' | 'play')입니다.
  private static final String KIND_STUDY = "study";

  public static StudyPlanResponse from(StudyPlan plan) {
    return new StudyPlanResponse(
        String.valueOf(plan.getId()),
        plan.getPlanDate(),
        String.valueOf(plan.getSubjectId()),
        plan.getContent(),
        plan.getTargetMinutes(),
        plan.isDone(),
        null,
        KIND_STUDY,
        null);
  }
}
