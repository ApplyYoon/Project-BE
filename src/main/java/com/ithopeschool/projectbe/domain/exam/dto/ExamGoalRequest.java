package com.ithopeschool.projectbe.domain.exam.dto;

import jakarta.validation.constraints.Pattern;

// "시험을 목표에 잇기 · 떼기" 요청의 모양입니다. (이슈 #228)
//   { "goalId": "4" }   → 4번 목표에 잇기
//   { "goalId": null }  → 목표에서 떼기
// - 내 점수형 목표인지는 Service 에서 검사합니다. (남의 목표 · 없는 목표는 404, 완주형 목표는 400)
public record ExamGoalRequest(
    // 18자리까지만 받습니다. 그보다 길면 서버의 숫자(Long)에 안 들어가서 500 이 나기 때문입니다.
    @Pattern(regexp = "\\d{1,18}", message = "목표 번호가 올바르지 않아요") String goalId) {}
