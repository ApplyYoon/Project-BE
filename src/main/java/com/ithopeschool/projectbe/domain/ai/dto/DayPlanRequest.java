package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// AI 하루 계획 요청의 모양입니다.
//   { "date": "2026-10-18" }
// - date: 계획을 짤 날짜. 오늘부터 14일 안이어야 합니다. (Service 가 확인)
// 내 기록 · 과목 · 시험 · 설문 · 심리검사는 보내지 않습니다. 서버가 DB 에서 읽습니다.
public record DayPlanRequest(@NotNull(message = "계획을 짤 날짜를 보내 주세요") LocalDate date) {}
