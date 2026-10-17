package com.ithopeschool.projectbe.domain.onboarding;

import java.util.ArrayList;
import java.util.List;

// 온보딩 설문의 "주로 공부하는 장소". (FE onboardingSteps.ts 의 studyPlace 보기)
// DB 에는 이름 그대로(LIBRARY) 저장하고, FE 와 주고받을 때는 화면 글자("독서실·도서관")로 바꿉니다.
public enum StudyPlace {
  HOME("집"),
  SCHOOL("학교"),
  ACADEMY("학원"),
  LIBRARY("독서실·도서관"),
  CAFE("카페"),
  OTHER("기타");

  // FE 와 주고받는 글자
  private final String value;

  StudyPlace(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  // FE 가 보낸 글자를 장소로 바꿉니다. 정해진 값이 아니면 null 입니다.
  public static StudyPlace fromValue(String value) {
    for (StudyPlace place : values()) {
      if (place.value.equals(value)) {
        return place;
      }
    }
    return null;
  }

  // 에러 문장에 보여 줄 "고를 수 있는 값" 목록
  public static String allowedValues() {
    List<String> names = new ArrayList<>();
    for (StudyPlace place : values()) {
      names.add(place.value);
    }
    return String.join(", ", names);
  }
}
