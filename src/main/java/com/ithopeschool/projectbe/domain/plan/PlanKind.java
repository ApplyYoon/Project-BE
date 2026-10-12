package com.ithopeschool.projectbe.domain.plan;

import java.util.Locale;

// 계획의 종류. 공부 계획(STUDY)인지 노는 계획(PLAY)인지입니다. (Project#12)
// FE 의 PlanKind('study' | 'play') 와 같은 두 개입니다.
//
// DB 에는 이름 그대로(STUDY) 저장하고, FE 와 주고받을 때는 소문자(study)로 바꿉니다. (과목 색 SubjectColor 와 같은 방식)
//   STUDY.toValue()          -> "study"
//   PlanKind.fromValue("play") -> PLAY
public enum PlanKind {
  STUDY,
  PLAY;

  // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
  public String toValue() {
    // Locale.ROOT: 컴퓨터의 언어 설정에 따라 소문자가 달라지지 않게 합니다.
    return name().toLowerCase(Locale.ROOT);
  }

  // FE 가 보낸 글자를 종류로 바꿉니다. 두 가지 중 하나가 아니면 null 입니다.
  public static PlanKind fromValue(String value) {
    for (PlanKind kind : values()) {
      if (kind.toValue().equals(value)) {
        return kind;
      }
    }
    return null;
  }
}
