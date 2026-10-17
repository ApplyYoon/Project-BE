package com.ithopeschool.projectbe.domain.onboarding;

import java.util.ArrayList;
import java.util.List;

// 온보딩 설문의 "계획이 주로 언제 무너졌나". 여러 개 고를 수 있습니다. (FE 의 PastFailureCause)
// DB 에는 이름 그대로(AFTER_EXAM) 저장하고, FE 와 주고받을 때는 FE 글자("afterExam")로 바꿉니다.
public enum FailureCause {
  AFTER_EXAM("afterExam"),
  WEEKEND("weekend"),
  LATE_ACADEMY("lateAcademy"),
  PHONE("phone"),
  TOO_MUCH_PLAN("tooMuchPlan");

  // FE 와 주고받는 글자
  private final String value;

  FailureCause(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  // FE 가 보낸 글자를 무너진 때로 바꿉니다. 정해진 값이 아니면 null 입니다.
  public static FailureCause fromValue(String value) {
    for (FailureCause cause : values()) {
      if (cause.value.equals(value)) {
        return cause;
      }
    }
    return null;
  }

  // 에러 문장에 보여 줄 "고를 수 있는 값" 목록
  public static String allowedValues() {
    List<String> names = new ArrayList<>();
    for (FailureCause cause : values()) {
      names.add(cause.value);
    }
    return String.join(", ", names);
  }
}
