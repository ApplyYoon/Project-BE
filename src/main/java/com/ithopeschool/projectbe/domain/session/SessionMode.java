package com.ithopeschool.projectbe.domain.session;

// 세션을 잰 방법. (최종 기획서 「4.4 타이머」, 이슈 #224) FE 의 SessionMode('normal' | 'offline' | 'selfReport') 입니다.
//   NORMAL:      타이머로 잰 세션 (기본)
//   OFFLINE:     야자처럼 폰을 내야 할 때, 폰을 내고 앱이 잰 세션
//   SELF_REPORT: 끝난 뒤 사후 입력한 세션 (증빙이 없으면 의지력 계산에서 실행률 50%)
// 옛 기록은 이 칸이 비어 있고, 타이머 기록(NORMAL)으로 봅니다.
//
// DB 에는 이름 그대로(SELF_REPORT) 저장하고, FE 와 주고받을 때는 FE 글자(selfReport)로 바꿉니다.
//   SELF_REPORT.toValue()             -> "selfReport"
//   SessionMode.fromValue("offline")  -> OFFLINE
public enum SessionMode {
  NORMAL("normal"),
  OFFLINE("offline"),
  SELF_REPORT("selfReport");

  // FE 와 주고받는 글자. selfReport 처럼 소문자로만 바꿔서는 안 나오는 글자가 있어서 하나씩 적어 둡니다.
  private final String value;

  SessionMode(String value) {
    this.value = value;
  }

  public String toValue() {
    return value;
  }

  // FE 가 보낸 글자를 방법으로 바꿉니다. 3가지 중 하나가 아니면 null 입니다.
  public static SessionMode fromValue(String value) {
    for (SessionMode mode : values()) {
      if (mode.value.equals(value)) {
        return mode;
      }
    }
    return null;
  }
}
