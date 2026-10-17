package com.ithopeschool.projectbe.domain.gpt;

// GPT 를 부른 종류입니다. (이슈 #33) 지금 GPT 를 실제로 부르는 세 곳만 있습니다.
// 인사이트 문장 · 개선안 · 일기 분석처럼 GPT 를 부르는 기능이 새로 생기면 여기에 한 줄을 더합니다.
public enum GptUsageKind {
  // AI 와 음성 통화 (분당 과금. 통화 시간 한도를 따로 셉니다)
  CALL,
  // 통화가 끝난 뒤 대화를 요약 (CallReviewClient)
  CALL_REVIEW,
  // 주간·월간·어제 한 줄 코치 문단 (CoachAiClient)
  COACH
}
