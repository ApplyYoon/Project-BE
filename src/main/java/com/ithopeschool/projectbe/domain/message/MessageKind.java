package com.ithopeschool.projectbe.domain.message;

import java.util.Locale;

// AI 가 보낸 메시지의 종류입니다. (이슈 #30, 기획서 v2 2장 「AI 전화·메시지 설계」)
// 관리 강도(domain/care/CareLevel)의 단계 이름과 짝이 맞습니다.
//
//   RECOVERY  회복      (0단계) 무리하지 않게 쉬어 가자는 말
//   CARROT    당근      (1단계) 잘한 것을 알아주는 말
//   NUDGE     넛지      (2단계) 담백하게 한 번 건네는 말
//   STICK     채찍      (3~4단계) 독촉하는 말. 허용했을 때만 나갑니다
//   EVENT     이벤트 알림 무슨 일이 생겨서 바로 보낸 알림 (계획 시간 지남, 목표 달성 등)
public enum MessageKind {
  CARROT,
  NUDGE,
  STICK,
  EVENT,
  RECOVERY;

  // FE 와 주고받는 글자입니다. 소문자로 씁니다. 예: CARROT -> "carrot"
  public String toValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
