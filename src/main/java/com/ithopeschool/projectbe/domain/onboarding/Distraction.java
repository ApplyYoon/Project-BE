package com.ithopeschool.projectbe.domain.onboarding;

import java.util.ArrayList;
import java.util.List;

// 온보딩 설문의 "가장 큰 방해 요소". 자기 인식 항목입니다. (FE onboardingSteps.ts 의 biggestDistraction 보기)
// DB 에는 이름 그대로(PHONE) 저장하고, FE 와 주고받을 때는 화면 글자("폰·SNS")로 바꿉니다.
public enum Distraction {
  PHONE("폰·SNS"),
  SLEEPY("졸림"),
  WANDERING_MIND("잡생각"),
  PEOPLE("친구·가족"),
  NOISE("소음·주변 환경"),
  OTHER("기타");

  // FE 와 주고받는 글자
  private final String value;

  Distraction(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  // FE 가 보낸 글자를 방해 요소로 바꿉니다. 정해진 값이 아니면 null 입니다.
  public static Distraction fromValue(String value) {
    for (Distraction distraction : values()) {
      if (distraction.value.equals(value)) {
        return distraction;
      }
    }
    return null;
  }

  // 에러 문장에 보여 줄 "고를 수 있는 값" 목록
  public static String allowedValues() {
    List<String> names = new ArrayList<>();
    for (Distraction distraction : values()) {
      names.add(distraction.value);
    }
    return String.join(", ", names);
  }
}
