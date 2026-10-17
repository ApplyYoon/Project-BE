package com.ithopeschool.projectbe.domain.care;

// 계획을 못 채운 주된 이유입니다. 학생이 체크인에서 버튼으로 고르는 7개와 같습니다.
// FE 의 MissReason('tired' | 'event' | 'phone' | 'tooMuch' | 'unwilling' | 'sick' | 'etc') 과 같은
// 순서·같은 뜻입니다.
//
// 체크인을 저장하는 기능(이슈 #24)이 생기면 그쪽에서도 이 값을 같이 씁니다.
// 타이머를 목표 전에 그만둔 이유(공부 기록의 stopReason, 이슈 #13)도 이 7가지를 씁니다.
//
// DB 에는 이름 그대로(TOO_MUCH) 저장하고, FE 와 주고받을 때는 FE 글자(tooMuch)로 바꿉니다.
//   TOO_MUCH.toValue()               -> "tooMuch"
//   MissReason.fromValue("phone")    -> PHONE
public enum MissReason {
  // 피곤
  TIRED("tired"),
  // 약속·행사
  EVENT("event"),
  // 폰·SNS
  PHONE("phone"),
  // 계획 과다
  TOO_MUCH("tooMuch"),
  // 하기 싫음
  UNWILLING("unwilling"),
  // 아픔
  SICK("sick"),
  // 기타
  ETC("etc");

  // FE 와 주고받는 글자. tooMuch 처럼 소문자로만 바꿔서는 안 나오는 글자가 있어서 하나씩 적어 둡니다.
  private final String value;

  MissReason(String value) {
    this.value = value;
  }

  // FE 와 주고받는 글자로 바꿉니다.
  public String toValue() {
    return value;
  }

  // FE 가 보낸 글자를 이유로 바꿉니다. 7가지 중 하나가 아니면 null 입니다.
  public static MissReason fromValue(String value) {
    for (MissReason reason : values()) {
      if (reason.value.equals(value)) {
        return reason;
      }
    }
    return null;
  }
}
