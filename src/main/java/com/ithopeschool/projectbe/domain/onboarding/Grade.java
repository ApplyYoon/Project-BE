package com.ithopeschool.projectbe.domain.onboarding;

import java.util.ArrayList;
import java.util.List;

// 온보딩 설문의 학년. 이 다섯 가지 말고는 DB 에 들어갈 수 없습니다. (FE 의 OnboardingAnswers.grade)
//
// DB 에는 이름 그대로(HIGH_2) 저장하고, FE 와 주고받을 때는 화면 글자("고2")로 바꿉니다. (SubjectColor 와 같은 방식)
//   HIGH_2.getValue()          -> "고2"
//   Grade.fromValue("고2")     -> HIGH_2
public enum Grade {
  HIGH_1("고1"),
  HIGH_2("고2"),
  HIGH_3("고3"),
  REPEATER("N수"),
  UNIVERSITY("대학생");

  // FE 와 주고받는 글자
  private final String value;

  Grade(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  // FE 가 보낸 글자를 학년으로 바꿉니다. 다섯 가지 중 하나가 아니면 null 입니다.
  public static Grade fromValue(String value) {
    for (Grade grade : values()) {
      if (grade.value.equals(value)) {
        return grade;
      }
    }
    return null;
  }

  // 에러 문장에 보여 줄 "고를 수 있는 값" 목록. 예) "고1, 고2, 고3, N수, 대학생"
  public static String allowedValues() {
    List<String> names = new ArrayList<>();
    for (Grade grade : values()) {
      names.add(grade.value);
    }
    return String.join(", ", names);
  }
}
