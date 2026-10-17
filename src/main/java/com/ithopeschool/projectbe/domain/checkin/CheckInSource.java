package com.ithopeschool.projectbe.domain.checkin;

// 체크인을 어디서 만들었는지입니다. (이슈 #24)
// 분석할 때 "직접 답한 것" 과 "기분만 누른 것" 을 구분하려고 적어 둡니다.
//
// DB 에는 이름 그대로(QUICK) 저장하고, FE 와 주고받을 때는 FE 글자(quick)로 바꿉니다.
//   QUICK.toValue()                   -> "quick"
//   CheckInSource.fromValue("call")   -> CALL
public enum CheckInSource {
  // 체크인 화면에서 직접 입력 ("자세히 체크인")
  MANUAL("manual"),
  // AI 통화(#29) 에서 만든 체크인
  CALL("call"),
  // 타이머를 시작할 때 기분만 1탭으로 고른 체크인 (QuickCheckIn)
  QUICK("quick");

  // FE 와 주고받는 글자
  private final String value;

  CheckInSource(String value) {
    this.value = value;
  }

  // FE 와 주고받는 글자로 바꿉니다.
  public String toValue() {
    return value;
  }

  // FE 가 보낸 글자를 바꿉니다. 3가지 중 하나가 아니면 null 입니다.
  public static CheckInSource fromValue(String value) {
    for (CheckInSource source : values()) {
      if (source.value.equals(value)) {
        return source;
      }
    }
    return null;
  }
}
