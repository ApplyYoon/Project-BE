package com.ithopeschool.projectbe.domain.checkin.dto;

import java.time.LocalDate;
import java.util.List;

// "오늘 물어볼 질문" 의 모양입니다.
//   { "date": "2026-10-17", "basic": ["mood"],
//     "detail": ["condition", "expectedMinutes", "missReason", "promiseKept"] }
// - date: 서버가 "오늘" 로 본 날짜 (새벽 4시 기준)
// - basic: 기본으로 묻는 질문 (타이머 1탭)
// - detail: "자세히 체크인" 에서만 묻는 질문. 질문 이름은 체크인 저장의 필드 이름과 같습니다.
public record CheckInQuestionsResponse(LocalDate date, List<String> basic, List<String> detail) {}
