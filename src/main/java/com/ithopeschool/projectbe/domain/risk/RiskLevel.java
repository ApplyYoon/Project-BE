package com.ithopeschool.projectbe.domain.risk;

// 위험 예측의 위험 수준입니다. (이슈 #49 분석 ⑧) DB 에는 이름 그대로(WATCH) 저장합니다.
//   NONE  : 위험 신호 없음
//   WATCH : 주의 — 위험 신호가 하나
//   HIGH  : 높음 — 위험 신호가 둘 다
public enum RiskLevel {
  NONE,
  WATCH,
  HIGH;

  // 위험한 날인지(주의 이상). 통화 대본과 목표 조정에 "표시" 로 넘길 때 이 값을 봅니다.
  public boolean isRisky() {
    return this != NONE;
  }
}
