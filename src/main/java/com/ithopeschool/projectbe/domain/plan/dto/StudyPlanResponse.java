package com.ithopeschool.projectbe.domain.plan.dto;

import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// 계획 하나를 FE 에 알려 주는 모양입니다. FE 의 StudyPlan 과 같은 이름을 씁니다.
//   { "id": "7", "date": "2026-10-05", "subjectId": "3", "content": "수학 3단원",
//     "targetMinutes": 90, "done": false, "startTime": "19:00", "kind": "study",
//     "routineId": null }
// - id, subjectId, routineId 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - 노는 계획은 과목이 없어서 subjectId 가 빈 글자("")입니다. (FE 의 StudyPlan.subjectId 와 같은 약속)
// - startTime 은 "19:00" 모양, 안 정했으면 null 입니다.
// - kind 는 소문자(study / play)입니다.
// - 누구의 계획인지(userId)는 담지 않습니다.
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

  // LocalTime 을 그냥 글자로 바꾸면 "19:00:00" 처럼 초가 붙을 수 있어서, 몇 시 몇 분만 꺼냅니다.
  private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

  public static StudyPlanResponse from(StudyPlan plan) {
    return new StudyPlanResponse(
        String.valueOf(plan.getId()),
        plan.getPlanDate(),
        plan.getSubjectId() == null ? "" : String.valueOf(plan.getSubjectId()),
        plan.getContent(),
        plan.getTargetMinutes(),
        plan.isDone(),
        plan.getStartTime() == null ? null : plan.getStartTime().format(HOUR_MINUTE),
        plan.getKind().toValue(),
        plan.getRoutineId() == null ? null : String.valueOf(plan.getRoutineId()));
  }
}
