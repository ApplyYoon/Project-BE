package com.ithopeschool.projectbe.domain.coach;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// AI 코치가 "어느 기간" 을 보고 말하는지의 종류입니다. 종류마다 문장 수와 권할 수 있는 행동이 다릅니다.
// 숫자를 지어내지 않기·깎아내리지 않기 같은 안전 규칙은 종류와 상관없이 같습니다. (CoachPrompt)
//
// 새 자리에 AI 문장을 붙이려면: 여기에 한 줄을 더하고, CoachController 에 주소를 하나 더합니다.
@Getter
@RequiredArgsConstructor
public enum CoachKind {
  // 홈의 "어제 한 줄". 매일 보는 자리라 짧게 씁니다.
  DAILY(
      "어제 하루",
      "어제 숫자야.",
      "1~2문장",
      "오늘",
      List.of("오늘 첫 계획을 제시간에 시작하기", "오늘은 목표 시간을 조금 줄여서 시작하기", "어제만큼만 해 보기", "지금처럼 이어 가기")),
  // 주간 리포트의 코치 한마디
  WEEKLY(
      "한 주",
      "이번 주 숫자야.",
      "3~4문장",
      "다음 주",
      List.of("목표 시간을 조금 줄이기", "목표 시간을 조금 늘리기", "시작 시각에 알림 맞추기", "지금처럼 이어 가기")),
  // 월간 리포트의 코치 한마디 (최근 4주)
  MONTHLY(
      "최근 4주",
      "최근 4주 숫자야.",
      "3~4문장",
      "다음 달",
      List.of("목표 시간을 조금 줄이기", "목표 시간을 조금 늘리기", "시작 시각에 알림 맞추기", "지금처럼 이어 가기"));

  // 지침의 첫 줄에 들어가는 기간 이름. 예: "너는 고등학생의 한 주 공부 기록을 같이 보는 코치야"
  private final String period;
  // AI 에게 숫자를 건넬 때 맨 위에 적는 한 줄
  private final String inputTitle;
  // 몇 문장으로 쓸지
  private final String length;
  // 권하는 행동을 "언제" 해 볼지. 예: "다음 주에 해 볼 행동 하나를 권해"
  private final String adviceWhen;
  // 권해도 되는 행동들. AI 는 이 안에서만 고릅니다.
  private final List<String> adviceOptions;
}
