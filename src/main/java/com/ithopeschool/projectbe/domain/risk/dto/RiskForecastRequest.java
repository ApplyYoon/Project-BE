package com.ithopeschool.projectbe.domain.risk.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// "위험 예측하기" 요청의 모양입니다.
//   { "date": "2026-10-13" }
// - date: 예측할 날짜 (YYYY-MM-DD). 오늘이나 내일만 됩니다. 그 검사는 Service 에서 합니다.
public record RiskForecastRequest(@NotNull(message = "예측할 날짜를 보내 주세요") LocalDate date) {}
