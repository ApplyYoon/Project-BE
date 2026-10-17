package com.ithopeschool.projectbe.domain.card;

import java.util.Locale;

// 카드를 시작하라고 알려 주는 방법. (최종 기획서 「4.3 카드」 알림, 이슈 #222)
// FE 의 CardAlert.method('message' | 'call') 와 같은 두 개입니다.
//   MESSAGE: 알림 메시지 (기본)
//   CALL: AI 전화
//
// DB 에는 이름 그대로(CALL) 저장하고, FE 와 주고받을 때는 소문자(call)로 바꿉니다.
//   CALL.toValue()                    -> "call"
//   AlertMethod.fromValue("message")  -> MESSAGE
public enum AlertMethod {
  MESSAGE,
  CALL;

  // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
  public String toValue() {
    // Locale.ROOT: 컴퓨터의 언어 설정에 따라 소문자가 달라지지 않게 합니다.
    return name().toLowerCase(Locale.ROOT);
  }

  // FE 가 보낸 글자를 알림 방법으로 바꿉니다. 두 가지 중 하나가 아니면 null 입니다.
  public static AlertMethod fromValue(String value) {
    for (AlertMethod method : values()) {
      if (method.toValue().equals(value)) {
        return method;
      }
    }
    return null;
  }
}
