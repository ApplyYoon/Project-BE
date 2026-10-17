package com.ithopeschool.projectbe.domain.card;

import java.util.Locale;

// 카드의 우선순위. (최종 기획서 「4.3 카드」, 이슈 #222)
// FE 의 CardPriority('absolute' | 'flexible') 와 같은 두 개입니다.
//   ABSOLUTE: 절대 — 미룰 수 없는 카드 (과제 제출 등). 미루기 버튼이 없습니다.
//   FLEXIBLE: 유연 — 줄이거나 미룰 수 있는 카드 (운동 등). 정하지 않으면 이것입니다.
//
// DB 에는 이름 그대로(ABSOLUTE) 저장하고, FE 와 주고받을 때는 소문자(absolute)로 바꿉니다. (PlanKind 와 같은 방식)
//   ABSOLUTE.toValue()                 -> "absolute"
//   CardPriority.fromValue("flexible") -> FLEXIBLE
public enum CardPriority {
  ABSOLUTE,
  FLEXIBLE;

  // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
  public String toValue() {
    // Locale.ROOT: 컴퓨터의 언어 설정에 따라 소문자가 달라지지 않게 합니다.
    return name().toLowerCase(Locale.ROOT);
  }

  // FE 가 보낸 글자를 우선순위로 바꿉니다. 두 가지 중 하나가 아니면 null 입니다.
  public static CardPriority fromValue(String value) {
    for (CardPriority priority : values()) {
      if (priority.toValue().equals(value)) {
        return priority;
      }
    }
    return null;
  }
}
