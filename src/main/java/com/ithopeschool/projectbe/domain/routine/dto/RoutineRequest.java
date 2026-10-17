package com.ithopeschool.projectbe.domain.routine.dto;

import jakarta.validation.constraints.*;
import java.util.List;

// PATCH에서는 보내지 않은 칸을 유지합니다. POST의 필수 칸은 Service에서도 검사합니다.
public record RoutineRequest(
    @Size(min = 1, max = 7) List<@NotNull @Min(0) @Max(6) Integer> weekdays,
    @Pattern(regexp = "study|play") String kind,
    @Pattern(regexp = "\\d{0,18}") String subjectId,
    @Size(min = 1, max = 200) String content,
    @Min(1) @Max(600) Integer targetMinutes,
    @Pattern(regexp = "|([01]\\d|2[0-3]):[0-5]\\d") String startTime) {}
