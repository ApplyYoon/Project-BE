package com.ithopeschool.projectbe.domain.call;

// AI 와 무엇으로 이야기했는지입니다. (이슈 #238)
// 상담 채팅은 "글로 하는 통화" 라서 통화와 같은 기록(call_records)에 남기고, 이 값으로만 구분합니다.
// 그래서 마무리 · 요약 · 지난 기록으로 다음 질문 정하기가 통화와 똑같이 돌아갑니다.
//
// FE 와 주고받을 때는 소문자(voice / chat)로 바꿉니다.  CHAT.toValue() -> "chat"
public enum CallChannel {
  // 음성 통화 (OpenAI Realtime, 앱 안 전화)
  VOICE,
  // 상담 탭의 글 채팅
  CHAT;

  // FE 와 주고받는 글자
  public String toValue() {
    return name().toLowerCase();
  }
}
