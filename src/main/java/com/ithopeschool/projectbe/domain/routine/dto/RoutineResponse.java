package com.ithopeschool.projectbe.domain.routine.dto;

import com.ithopeschool.projectbe.domain.routine.Routine;
import java.util.List;

public record RoutineResponse(
    String id,
    List<Integer> weekdays,
    String kind,
    String subjectId,
    String content,
    int targetMinutes,
    String startTime) {
  public static RoutineResponse of(Routine r) {
    return new RoutineResponse(
        r.getId().toString(),
        r.days(),
        r.getKind().toValue(),
        r.getSubjectId() == null ? "" : r.getSubjectId().toString(),
        r.getContent(),
        r.getTargetMinutes(),
        r.getStartTime() == null ? null : r.getStartTime().toString());
  }
}
