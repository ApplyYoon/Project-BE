package com.ithopeschool.projectbe.domain.review;

import java.util.Locale;

// 회고의 종류. 시험 후 회고와 주간 회고 두 가지입니다. (이슈 #17)
//
// DB 에는 이름 그대로(EXAM) 저장하고, FE 와 주고받을 때는 소문자(exam)로 바꿉니다. (시험 종류 ExamType 과 같은 방식)
//   EXAM.toValue()                  -> "exam"
//   ReviewType.fromValue("weekly") -> WEEKLY
public enum ReviewType {
  // 시험 후 회고 3문항: ① 준비가 충분했나 ② 가장 아쉬운 점 ③ 다음에 바꿀 한 가지. 시험 하나에 연결됩니다.
  EXAM,
  // 주간 회고 3문항 이내: ① 이번 주 가장 잘한 것 ② 가장 아쉬운 것 ③ 다음 주에 바꿀 한 가지. 한 주(월~일)에 연결됩니다.
  WEEKLY;

  // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
  public String toValue() {
    // Locale.ROOT: 컴퓨터의 언어 설정에 따라 소문자가 달라지지 않게 합니다.
    return name().toLowerCase(Locale.ROOT);
  }

  // FE 가 보낸 글자를 종류로 바꿉니다. 두 가지 중 하나가 아니면 null 입니다.
  public static ReviewType fromValue(String value) {
    for (ReviewType type : values()) {
      if (type.toValue().equals(value)) {
        return type;
      }
    }
    return null;
  }
}
