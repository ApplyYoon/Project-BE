package com.ithopeschool.projectbe.domain.psychtest;

// 온보딩 심리검사로 재는 성향 척도 7개입니다. (이슈 #219, 기획서 6.1, FE src/lib/psychTest.ts 의 PsychScale)
// FE 와 주고받을 때는 FE 와 같은 camelCase 이름(value)을 씁니다.   START_RESISTANCE.toValue() -> "startResistance"
// 순서는 기획서 6.1 표와 같습니다.
public enum PsychScale {
  // 시작 저항: 마음먹고 시작하기까지 꾸물거림
  START_RESISTANCE("startResistance", "시작 저항"),
  // 주의 분산: 공부 중 폰·딴생각에 흔들림
  DISTRACTION("distraction", "주의 분산"),
  // 계획 낙관: 계획을 과하게 세움
  PLAN_OPTIMISM("planOptimism", "계획 낙관"),
  // 기록 부담: 입력 자체를 귀찮아함
  RECORD_BURDEN("recordBurden", "기록 부담"),
  // 실패 민감도: 한 번 무너지면 포기·자책
  FAIL_SENSITIVITY("failSensitivity", "실패 민감도"),
  // 외부 귀인: 원인을 밖에서 찾음
  EXTERNAL_ATTRIBUTION("externalAttribution", "외부 귀인"),
  // 관리 선호: 따끔한 관리를 원함
  CARE_PREFERENCE("carePreference", "관리 선호");

  // FE 와 주고받는 이름
  private final String value;
  // 화면에 보여 주는 이름
  private final String label;

  PsychScale(String value, String label) {
    this.value = value;
    this.label = label;
  }

  public String toValue() {
    return value;
  }

  public String label() {
    return label;
  }
}
