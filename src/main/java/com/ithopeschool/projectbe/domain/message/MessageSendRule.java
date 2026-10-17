package com.ithopeschool.projectbe.domain.message;

import com.ithopeschool.projectbe.domain.safety.SafetyBrake;

// "지금 이 메시지를 알림으로 보내도 되는지" 를 판단합니다. (이슈 #32 웹 푸시, 기획서 v2 2장 「AI 전화·메시지 설계」)
// 메시지를 만들었더라도 푸시로 내보내기 직전에 이 검사를 한 번 더 거칩니다.
//
// 위에서부터 차례로 따지고, 먼저 맞는 줄에서 끝냅니다.
//
//   1) 연락 금지 시간이면 무엇이든 보내지 않습니다.
//        당근(1) 메시지, 한도 4, 연락 금지 시간                 →  false
//   2) 사용자가 정한 최대 한도보다 센 메시지는 보내지 않습니다. (한도는 어떤 경우에도 우선)
//        채찍(3) 메시지, 한도 2                                →  false
//        넛지(2) 메시지, 한도 2                                →  true   (한도와 같으면 보냅니다)
//   3) 안전 브레이크가 켜졌으면 채찍 메시지(3~4단계)는 보내지 않습니다.
//        채찍(3) 메시지, 한도 4, 위기 표현 감지(stopWhip)       →  false
//        채찍(3) 메시지, 한도 4, 회복 모드(recoveryMode)        →  false
//   4) 그 외에는 보냅니다. 브레이크가 켜져도 회복·당근·넛지(0~2단계)는 그대로 보냅니다.
//        당근(1) 메시지, 한도 4, 위기 표현 감지(stopWhip)       →  true
//
// FE 의 canSendAiMessageNotification (src/lib/notifications.ts) 과 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 앱이 켜져 있을 때와 꺼져 있을 때의 알림이 달라지지 않습니다.
public final class MessageSendRule {

  // 관리 강도가 이 값 이상이면 "채찍(독촉)" 메시지로 봅니다. (3 심리적 채찍, 4 강제적 채찍)
  public static final int WHIP_CARE_LEVEL = 3;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private MessageSendRule() {}

  // 지금 이 메시지를 알림으로 보내도 되면 true 를 돌려줍니다.
  //
  // careLevel: 보내려는 메시지의 관리 강도 (0 회복 ~ 4 강제적 채찍)
  // brake: 안전 브레이크 판정 결과 (SafetyBrake.check)
  // maxCareLevel: 사용자가 정한 관리 강도 최대 한도. 이 단계보다 센 메시지는 보내지 않습니다.
  public static boolean canSend(int careLevel, SafetyBrake brake, int maxCareLevel) {
    // 1) 연락 금지 시간
    if (brake.quiet()) {
      return false;
    }
    // 2) 사용자가 정한 한도
    if (careLevel > maxCareLevel) {
      return false;
    }
    // 3) 안전 브레이크가 켜졌으면 채찍은 보내지 않습니다.
    boolean isSafetyBrakeOn = brake.stopWhip() || brake.recoveryMode();
    if (isSafetyBrakeOn && careLevel >= WHIP_CARE_LEVEL) {
      return false;
    }
    return true;
  }
}
