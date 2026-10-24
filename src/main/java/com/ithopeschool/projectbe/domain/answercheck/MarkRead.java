package com.ithopeschool.projectbe.domain.answercheck;

// "채점했어요"(MARKED) 모드에서 AI 가 사진에서 읽은 학생의 채점 표시입니다.
// 표시가 없거나 세모 · 지운 자국처럼 O · X 로 읽을 수 없으면 값이 비어 있고(null), 판정은 애매(UNSURE)입니다.
public enum MarkRead {
  // 동그라미 (맞음 표시)
  O,
  // 가위표 · 빗금 (틀림 표시)
  X
}
