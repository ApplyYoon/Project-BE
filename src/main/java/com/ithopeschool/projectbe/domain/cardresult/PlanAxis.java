package com.ithopeschool.projectbe.domain.cardresult;

// 계획 축 — 체크리스트가 남았을 때 어느 쪽 문제인지. (최종 기획서 「5.1」 계획 축, 이슈 #229)
// FE 의 getPlanAxis 결과('complete' | 'overPlanned' | 'execution') 와 같은 세 가지입니다.
//   COMPLETE:     체크리스트를 다 했음
//   OVER_PLANNED: 실행률 80% 이상인데 남음 → 계획이 컸음 (채찍 재료가 아니라 계획 크기를 고칠 재료)
//   EXECUTION:    실행률 80% 미만이고 남음 → 늦은 시작 · 중도 종료 (실행 축 문제)
// 체크리스트가 남은 날을 '실패' 라고 부르지 않습니다. 앱이 흑백 사고(6번 오류)를 하게 되기 때문입니다.
public enum PlanAxis {
  COMPLETE("complete"),
  OVER_PLANNED("overPlanned"),
  EXECUTION("execution");

  // FE 와 주고받는 글자
  private final String value;

  PlanAxis(String value) {
    this.value = value;
  }

  public String toValue() {
    return value;
  }
}
