package com.ithopeschool.projectbe.domain.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 이벤트 메시지의 종류입니다. (이슈 #31, 기획서 v2 2장 「AI 전화·메시지 설계」)
// 정해진 빈도의 정기 메시지와 별도로, 무슨 일이 생겼을 때 바로 보내는 메시지입니다.
// 지금 어느 것을 보낼지는 EventMessage.pick 이 정합니다.
//
// FE 의 EventMessageType('planLate' | 'goalDone' | 'missed3Days') 과 같은 세 개입니다.
// label 은 FE 의 eventMessageLabels, careLevel 은 FE 의 eventMessageCareLevels 와 같은 값입니다.
@Getter
@RequiredArgsConstructor
public enum EventMessageType {
  // 계획 시간이 지났는데 타이머가 꺼져 있음 → 담백한 알림 (넛지)
  PLAN_LATE("계획 시간 지남", 2),
  // 오늘 목표를 달성함 → 칭찬 (당근)
  GOAL_DONE("목표 달성", 1),
  // 3일 연속 목표 미달 → 회복. 독촉(채찍)이 아닙니다
  MISSED_3_DAYS("3일 연속 미달", 0);

  // 메시지 말풍선 아래에 붙는 짧은 이름
  private final String label;

  // 이 메시지를 보낼 때의 관리 강도 (0 회복 · 1 당근 · 2 넛지). 3일 연속 미달은 채찍이 아니라 회복(0)입니다.
  private final int careLevel;
}
