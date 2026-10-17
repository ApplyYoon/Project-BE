package com.ithopeschool.projectbe.domain.message;

import com.ithopeschool.projectbe.domain.safety.QuietPolicy;
import java.util.Locale;

// AI 가 보낸 메시지의 종류입니다. (이슈 #30, 기획서 v2 2장 「AI 전화·메시지 설계」)
// 관리 강도(domain/care/CareLevel)의 단계 이름과 짝이 맞습니다.
//
//   RECOVERY  회복      (0단계) 무리하지 않게 쉬어 가자는 말
//   CARROT    당근      (1단계) 잘한 것을 알아주는 말
//   NUDGE     넛지      (2단계) 담백하게 한 번 건네는 말
//   STICK     채찍      (3~4단계) 독촉하는 말. 허용했을 때만 나갑니다
//   EVENT     이벤트 알림 무슨 일이 생겨서 바로 보낸 알림 (계획 시간 지남, 목표 달성 등)
//   CALL      전화 알림   학생이 정한 AI 전화 시간이 됐다는 알림 (#32, CallRingJob). 관리 강도와는 상관없습니다
public enum MessageKind {
  CARROT,
  NUDGE,
  STICK,
  EVENT,
  RECOVERY,
  CALL;

  // FE 와 주고받는 글자입니다. 소문자로 씁니다. 예: CARROT -> "carrot"
  public String toValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  // 연락 금지 시간에 걸리면 미룰지(DELAY) 버릴지(DROP)입니다. (이슈 #57, QuietHours.decide)
  //   당근 · 넛지 · 회복  →  미룸 (아침에 받아도 뜻이 있음)
  //   채찍(독촉)          →  버림 (어젯밤 독촉을 아침에 받으면 엉뚱함)
  //   전화 알림           →  버림 (지난밤 전화 시간을 아침에 알리면 엉뚱함)
  //   이벤트              →  종류마다 달라서 EventMessageType.getQuietPolicy() 를 씁니다.
  //                          여기서는 덜 연락하는 쪽(버림)으로 둡니다. (미성년자라 연락은 적은 쪽이 안전)
  public QuietPolicy quietPolicy() {
    return switch (this) {
      case CARROT, NUDGE, RECOVERY -> QuietPolicy.DELAY;
      case STICK, EVENT, CALL -> QuietPolicy.DROP;
    };
  }
}
