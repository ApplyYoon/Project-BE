package com.ithopeschool.projectbe.domain.ai.dto;

import com.ithopeschool.projectbe.domain.ai.CoachNudge;

// "코치 알림 문장" 의 결과입니다. 아무것도 저장하지 않습니다. (2026-10-18)
//   { "morning": { "title": "오늘 6교시 미적분", "body": "어제 틀린 극한 문제, 쉬는 시간에 한 번만 다시 보자." },
//     "evening": { "title": "오늘 카드 어땠어?", "body": "미적분 문제집 해 봤어? 내일 계획도 같이 정하자." },
//     "comfort": false }
// - morning · evening 이 null 이면 그 칸은 만들지 못한 것입니다. (AI 꺼짐 · 하루 한도 · 실패 · 검사에서 빠짐)
//   에러가 아니라 200 이고, FE 는 그 칸만 정해 둔 문장으로 예약합니다.
// - comfort 가 true 면 최근 위기 표현 · 보호 조치 때문에 AI 를 부르지 않고 정해 둔 위로 문장을 준 것입니다.
//   FE 는 이 동안 공부를 재촉하는 다른 알림도 줄이는 것이 좋습니다. (HabitNudgeJob 이 "오늘의 작전" 을 멈추는 것과 같은 뜻)
public record CoachNudgeResponse(
    CoachNudge.Nudge morning, CoachNudge.Nudge evening, boolean comfort) {

  // 위로 문장 (공부를 재촉하지 않고, AI 임을 숨기지 않습니다)
  static final CoachNudge.Nudge COMFORT_MORNING =
      new CoachNudge.Nudge("AI 코치야", "오늘은 공부 얘기보다 네 하루가 궁금해. 편할 때 말 걸어 줘.");
  static final CoachNudge.Nudge COMFORT_EVENING =
      new CoachNudge.Nudge("오늘 하루 어땠어?", "힘든 일이 있으면 언제든 이야기해 줘. 혼자 견디지 않아도 괜찮아.");

  // 검사한 결과
  public static CoachNudgeResponse of(CoachNudge.Result result) {
    return new CoachNudgeResponse(result.morning(), result.evening(), false);
  }

  // 만들지 못했을 때 (AI 꺼짐 · 하루 한도 · 실패)
  public static CoachNudgeResponse notMade() {
    return new CoachNudgeResponse(null, null, false);
  }

  // 최근 위기 표현 · 보호 조치 중이거나, 보낸 글에 위기 표현이 있을 때. AI 를 부르지 않습니다.
  public static CoachNudgeResponse comforting() {
    return new CoachNudgeResponse(COMFORT_MORNING, COMFORT_EVENING, true);
  }
}
