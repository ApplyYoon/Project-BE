package com.ithopeschool.projectbe.domain.safety;

// 연락 금지 시간에 걸린 메시지를 어떻게 할지입니다. (이슈 #57 "미룰 메시지와 버릴 메시지를 구분했다")
//
// 기준: "아침에 받아도 여전히 뜻이 있는가?" (2026-10-17 학생이 정함)
//   DELAY  미룸  금지 시간이 끝난 뒤에 보냅니다. 예) 목표 달성 칭찬, 3일 연속 미달 회복, 당근, 회복, 오늘의 작전
//   DROP   버림  보내지 않습니다.              예) "계획 시간 지났어", 독촉(채찍) — 아침에 받으면 엉뚱합니다
//
// 메시지 종류마다 어느 쪽인지는 MessageKind.quietPolicy() · EventMessageType.getQuietPolicy() 에 적어 두었습니다.
public enum QuietPolicy {
  DELAY,
  DROP
}
