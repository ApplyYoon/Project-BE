package com.ithopeschool.projectbe.domain.health;

// 응답 모양(DTO)입니다. record 는 값만 담는 짧은 클래스이고, 응답으로 나갈 때 JSON 으로 바뀝니다.
// status: 서버 상태 ("ok") / db: DB 상태 ("connected" 또는 "error")
public record HealthResponse(String status, String db) {}
