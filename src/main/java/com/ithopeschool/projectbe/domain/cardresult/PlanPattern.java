package com.ithopeschool.projectbe.domain.cardresult;

// 계획 패턴 — 같은 목표 카드의 최근 4주 체크리스트 달성률로 본 계획 크기. (최종 기획서 「5.1」 계획 축, 이슈 #229)
// FE 의 PlanPattern('over' | 'fit' | 'loose') 와 같은 세 가지입니다.
//   OVER:  과대 — 평균 60% 밑 (계획이 너무 큼)
//   FIT:   적정 — 60% ~ 90%
//   LOOSE: 느슨 — 평균 90% 초과 (계획이 너무 작음)
public enum PlanPattern {
  OVER("over"),
  FIT("fit"),
  LOOSE("loose");

  // FE 와 주고받는 글자
  private final String value;

  PlanPattern(String value) {
    this.value = value;
  }

  public String toValue() {
    return value;
  }
}
