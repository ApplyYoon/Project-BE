package com.ithopeschool.projectbe.domain.care;

// 재발 신호가 어느 조건 때문인지. (최종 기획서 「5.2 재발 신호」, 이슈 #230)
// FE 의 RelapseReason('routineTwice' | 'rateDrop' | 'absoluteMiss') 와 같은 세 가지입니다.
//   ROUTINE_TWICE: ① 같은 루틴 카드 2회 연속 미실행 (미루기 또는 실패)
//   RATE_DROP:     ② 최근 7일 실행률이 개인 기준선(직전 14일)의 70% 미만
//   ABSOLUTE_MISS: ③ 절대 카드 미실행 1회
public enum RelapseReason {
  ROUTINE_TWICE("routineTwice"),
  RATE_DROP("rateDrop"),
  ABSOLUTE_MISS("absoluteMiss");

  // FE 와 주고받는 글자
  private final String value;

  RelapseReason(String value) {
    this.value = value;
  }

  public String toValue() {
    return value;
  }
}
