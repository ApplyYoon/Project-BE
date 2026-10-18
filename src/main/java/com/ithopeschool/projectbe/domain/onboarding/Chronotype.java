package com.ithopeschool.projectbe.domain.onboarding;

import java.util.ArrayList;
import java.util.List;

// 온보딩 설문의 "아침형 / 저녁형". 자기 인식 항목입니다. (FE 의 Chronotype = 'morning' | 'night')
// DB 에는 이름 그대로(MORNING) 저장하고, FE 와 주고받을 때는 FE 글자("morning")로 바꿉니다.
public enum Chronotype {
  MORNING("morning"),
  NIGHT("night");

  // FE 와 주고받는 글자
  private final String value;

  Chronotype(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  // FE 가 보낸 글자를 아침형·저녁형으로 바꿉니다. 정해진 값이 아니면 null 입니다.
  public static Chronotype fromValue(String value) {
    for (Chronotype chronotype : values()) {
      if (chronotype.value.equals(value)) {
        return chronotype;
      }
    }
    return null;
  }

  // 에러 문장에 보여 줄 "고를 수 있는 값" 목록
  public static String allowedValues() {
    List<String> names = new ArrayList<>();
    for (Chronotype chronotype : values()) {
      names.add(chronotype.value);
    }
    return String.join(", ", names);
  }
}
