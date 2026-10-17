package com.ithopeschool.projectbe.domain.exam;

import java.util.Locale;

// 시험의 종류. 모의고사·내신·쪽지시험·기타 네 가지입니다. (이슈 #16)
// FE 의 ExamType('mock' | 'school' | 'quiz' | 'etc') 과 같은 네 개입니다.
//
// DB 에는 이름 그대로(MOCK) 저장하고, FE 와 주고받을 때는 소문자(mock)로 바꿉니다. (계획 종류 PlanKind 와 같은 방식)
//   MOCK.toValue()             -> "mock"
//   ExamType.fromValue("quiz") -> QUIZ
public enum ExamType {
  // 모의고사
  MOCK,
  // 내신 (학교 시험)
  SCHOOL,
  // 쪽지시험
  QUIZ,
  // 기타
  ETC;

  // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
  public String toValue() {
    // Locale.ROOT: 컴퓨터의 언어 설정에 따라 소문자가 달라지지 않게 합니다.
    return name().toLowerCase(Locale.ROOT);
  }

  // FE 가 보낸 글자를 종류로 바꿉니다. 네 가지 중 하나가 아니면 null 입니다.
  public static ExamType fromValue(String value) {
    for (ExamType type : values()) {
      if (type.toValue().equals(value)) {
        return type;
      }
    }
    return null;
  }
}
