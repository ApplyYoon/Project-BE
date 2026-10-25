package com.ithopeschool.projectbe.domain.answercheck;

// 문제 하나의 판정입니다. (2026-10-24 사용자 결정)
public enum GradeResult {
  // 맞음
  CORRECT,
  // 틀림
  WRONG,
  // 애매함. AI 가 자신 없거나 글씨 · 표시를 못 읽은 문제입니다. 억지로 틀림으로 하지 않습니다.
  UNSURE
}
