package com.ithopeschool.projectbe.domain.answercheck;

// 사진 채점의 모드입니다. 학생이 폰에서 사진을 올리기 전에 고릅니다. (2026-10-24 사용자 결정)
public enum AnswerCheckMode {
  // "채점했어요": 학생이 이미 O · X 로 채점한 페이지. AI 는 문제를 풀지 않고 문제 번호와 O · X 표시만 읽습니다.
  MARKED,
  // "아직 안 했어요": 채점하지 않은 페이지. AI 가 문제를 직접 풀고 학생 답과 비교합니다.
  UNMARKED
}
