package com.ithopeschool.projectbe.domain.cardresult;

// 카드 하루치(그 카드 · 그 날짜)의 결과. (최종 기획서 「5.1 카드 결과」, 이슈 #229)
// FE 의 CardResult (src/lib/cardResult.ts) 와 같은 여덟 가지입니다.
//   DONE:        실행 — 예정 시각 10분 안에 시작, 예정 종료까지
//   LATE:        지연 실행 — 10분 넘게 늦게 시작 (카드 시간 안)
//   ENDED_EARLY: 중도 종료 — 예정 종료 전에 [끝내기]
//   POSTPONED:   미루기
//   FAILED:      실패로 기록
//   REST:        사후 휴식 처리 (모든 집계에서 뺍니다)
//   SELF_REPORT: 사후 입력
//   UNHANDLED:   카드 시간이 끝났는데 시작도, 처리도 안 함 → 다음 통화에서 묻습니다 (집계에서는 실패로 셈)
//
// 결과는 DB 에 저장하지 않고, 카드 · 세션 · 표시로 그때그때 계산합니다. (CardResultJudge)
// FE 와 주고받을 때는 FE 글자(endedEarly)로 바꿉니다.
//   ENDED_EARLY.toValue() -> "endedEarly"
public enum CardResult {
  DONE("done"),
  LATE("late"),
  ENDED_EARLY("endedEarly"),
  POSTPONED("postponed"),
  FAILED("failed"),
  REST("rest"),
  SELF_REPORT("selfReport"),
  UNHANDLED("unhandled");

  // FE 와 주고받는 글자. endedEarly 처럼 소문자로만 바꿔서는 안 나오는 글자가 있어서 하나씩 적어 둡니다.
  private final String value;

  CardResult(String value) {
    this.value = value;
  }

  public String toValue() {
    return value;
  }

  // 미실행(미루기 · 실패 · 미처리)인지. 재발 신호와 미루기·실패 비율에 씁니다. (FE 의 isMissResult)
  public boolean isMiss() {
    return this == POSTPONED || this == FAILED || this == UNHANDLED;
  }
}
