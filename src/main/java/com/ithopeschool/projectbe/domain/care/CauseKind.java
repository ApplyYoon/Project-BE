package com.ithopeschool.projectbe.domain.care;

// 못 한 이유(MissReason)가 어디서 왔는지의 분류입니다. (최종 기획서 「5.4 관리 단계」, 이슈 #225)
// FE 의 CauseKind (src/lib/careStage.ts 의 getCauseKind) 와 같은 네 가지입니다.
//   INTERNAL:    내 쪽 — 폰·SNS · 하기 싫음 · 미루다 보니. 재발 신호와 겹치면 관리 단계를 올립니다
//   UNAVOIDABLE: 어쩔 수 없음 — 아픔 · 행사·약속
//   OVERLOAD:    계획이 많았거나 피곤 — 계획 과다 · 피곤·수면
//   UNKNOWN:     모름 — 기타
// 내부/외부로 둘만 나눌 때는 INTERNAL 이 내부, 나머지가 외부입니다.
public enum CauseKind {
  INTERNAL,
  UNAVOIDABLE,
  OVERLOAD,
  UNKNOWN
}
