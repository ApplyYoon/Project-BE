package com.ithopeschool.projectbe.domain.card;

import java.util.Locale;

// 카드의 유형. (최종 기획서 「4.3 카드」, 이슈 #222)
// FE 의 CardType('self' | 'lecture') 와 같은 두 개입니다.
//   SELF: 자습. 정하지 않으면 이것입니다.
//   LECTURE: 인강. 영상을 보러 다른 앱으로 나가도 세션이 이어집니다. (「4.4 타이머」)
//
// DB 에는 이름 그대로(LECTURE) 저장하고, FE 와 주고받을 때는 소문자(lecture)로 바꿉니다.
//   LECTURE.toValue()          -> "lecture"
//   CardType.fromValue("self") -> SELF
public enum CardType {
  SELF,
  LECTURE;

  // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
  public String toValue() {
    // Locale.ROOT: 컴퓨터의 언어 설정에 따라 소문자가 달라지지 않게 합니다.
    return name().toLowerCase(Locale.ROOT);
  }

  // FE 가 보낸 글자를 유형으로 바꿉니다. 두 가지 중 하나가 아니면 null 입니다.
  public static CardType fromValue(String value) {
    for (CardType type : values()) {
      if (type.toValue().equals(value)) {
        return type;
      }
    }
    return null;
  }
}
