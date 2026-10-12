package com.ithopeschool.projectbe.domain.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 앱 알림의 종류입니다. (이슈 #31)
// 앞의 셋(PLAN_LATE · GOAL_DONE · MISSED_3_DAYS)은 EventMessage.pick 이 판단하는 종류(EventMessageType)와 같고,
// 여기에 타이머 완주와 지난주 목표 결과가 더해집니다.
//
//   알림 종류            언제                                         기본 말투
//   PLAN_LATE            계획한 시작 시각이 지났는데 타이머가 꺼져 있음   넛지(2)
//   TIMER_DONE           타이머 목표 시간을 채워서 끝냄                 당근(1)
//   GOAL_DONE            오늘 공부 계획의 시간을 다 채움                당근(1)
//   MISSED_3_DAYS        3일 연속으로 계획을 못 채움                    회복(0)
//   WEEK_GOAL_ACHIEVED   지난주에 고른 목표를 달성함                    당근(1)
//   WEEK_GOAL_MISSED     지난주에 고른 목표를 못 채움                   넛지(2)
//
// FE 의 AppAlertType · appAlertLabels · appAlertCareLevels (src/lib/appAlert.ts) 와 같은 값입니다.
@Getter
@RequiredArgsConstructor
public enum AppAlertType {
  PLAN_LATE(EventMessageType.PLAN_LATE.getLabel(), EventMessageType.PLAN_LATE.getCareLevel()),
  TIMER_DONE("타이머 완주", 1),
  GOAL_DONE(EventMessageType.GOAL_DONE.getLabel(), EventMessageType.GOAL_DONE.getCareLevel()),
  MISSED_3_DAYS(
      EventMessageType.MISSED_3_DAYS.getLabel(), EventMessageType.MISSED_3_DAYS.getCareLevel()),
  WEEK_GOAL_ACHIEVED("주간 목표 달성", 1),
  WEEK_GOAL_MISSED("주간 목표 결과", 2);

  // 알림 말풍선 아래에 붙는 짧은 이름
  private final String label;

  // 이 종류의 기본 말투 (관리 강도. 0 회복 · 1 당근 · 2 넛지). 실제로는 여기서 사용자의 한도만큼 낮아질 수 있습니다.
  private final int careLevel;

  // EventMessage.pick 이 고른 종류를 앱 알림의 종류로 바꿉니다.
  public static AppAlertType from(EventMessageType type) {
    return switch (type) {
      case PLAN_LATE -> PLAN_LATE;
      case GOAL_DONE -> GOAL_DONE;
      case MISSED_3_DAYS -> MISSED_3_DAYS;
    };
  }
}
