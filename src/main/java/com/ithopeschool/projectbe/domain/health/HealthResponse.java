package com.ithopeschool.projectbe.domain.health;

// 응답 모양(DTO)입니다. record 는 값만 담는 짧은 클래스이고, 응답으로 나갈 때 JSON 으로 바뀝니다.
// status: 서버 상태 ("ok") / db: DB 상태 ("connected" 또는 "error")
// ai    : 글 AI(채팅 · 계획 · 문제 등)를 부를 수 있는지 — OpenAI 키와 글 모델 이름이 둘 다 있으면 true
// call  : AI 음성 통화의 임시 키를 만들 수 있는지 — OpenAI 키와 음성 모델 이름(OPENAI_REALTIME_MODEL)이 둘 다 있으면 true
// neis  : NEIS 인증키가 있는지 — 없으면 NEIS 가 한 번에 5건만 줘서 시간표가 일부만 옵니다
// ⚠️ ai · call · neis 는 "켜져 있는지" 참/거짓뿐입니다. 키 값 · 모델 이름 · 길이는 절대 담지 않습니다.
public record HealthResponse(String status, String db, boolean ai, boolean call, boolean neis) {}
