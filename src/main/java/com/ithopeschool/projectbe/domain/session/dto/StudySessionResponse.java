package com.ithopeschool.projectbe.domain.session.dto;

import com.ithopeschool.projectbe.domain.session.StudySession;

// 공부 기록 하나를 FE 에 알려 주는 모양입니다. FE 의 StudySession 과 같은 이름을 씁니다.
//   { "id": "12", "subjectId": "3", "startedAt": 1790000000000, "endedAt": 1790003000000,
//     "pauseCount": 2, "pausedMs": 300000, "planId": "7", "targetMinutes": 50,
//     "stopReason": "phone", "kind": "study" }
// - id, subjectId, planId 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - 노는 기록은 과목이 없어서 subjectId 가 빈 글자("")입니다. (계획과 같은 약속)
// - 시각은 밀리초 숫자입니다. (docs/API.md 2-2)
// - planId, targetMinutes, stopReason 은 없으면 null 입니다.
// - 누구의 기록인지(userId)는 담지 않습니다.
public record StudySessionResponse(
    String id,
    String subjectId,
    long startedAt,
    long endedAt,
    int pauseCount,
    long pausedMs,
    String planId,
    Integer targetMinutes,
    String stopReason,
    String kind) {

  public static StudySessionResponse from(StudySession session) {
    return new StudySessionResponse(
        String.valueOf(session.getId()),
        session.getSubjectId() == null ? "" : String.valueOf(session.getSubjectId()),
        session.getStartedAt().toEpochMilli(),
        session.getEndedAt().toEpochMilli(),
        session.getPauseCount(),
        session.getPausedMs(),
        session.getPlanId() == null ? null : String.valueOf(session.getPlanId()),
        session.getTargetMinutes(),
        session.getStopReason() == null ? null : session.getStopReason().toValue(),
        session.getKind().toValue());
  }
}
