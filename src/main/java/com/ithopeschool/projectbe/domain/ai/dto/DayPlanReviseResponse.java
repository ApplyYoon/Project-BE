package com.ithopeschool.projectbe.domain.ai.dto;

import com.ithopeschool.projectbe.domain.ai.DayPlanRevise;
import java.util.List;

// "계획 핑퐁" 의 결과입니다. 아무것도 저장하지 않습니다. (2026-10-18)
//   { "showHelp": false, "reply": "영어는 빼고 미적분을 40분으로 늘렸어.",
//     "drafts": [ { "date": "2026-10-19", "startTime": "19:00", "durationMinutes": 40,
//                   "title": "미적분 문제집", "checklist": ["3단원 10문제"] } ] }
// - drafts 가 null 이면 고치지 못한 것입니다. (AI 꺼짐 · 실패 · 쓸 카드 없음) FE 는 지금 계획을 그대로 둡니다.
//   빈 목록([])이면 학생이 다 빼 달라고 해서 계획이 비었다는 뜻입니다.
// - reply 는 무엇을 바꿨는지 코치의 한 줄입니다. 못 고쳤으면 null 입니다.
// - showHelp 가 true 면 요청에서 위기 표현이 감지된 것입니다. AI 를 부르지 않았고 drafts 는 null 입니다.
public record DayPlanReviseResponse(
    boolean showHelp, String reply, List<DayPlanRevise.Card> drafts) {

  public static DayPlanReviseResponse of(DayPlanRevise.Result result) {
    return result.cards() == null
        ? new DayPlanReviseResponse(false, null, null)
        : new DayPlanReviseResponse(false, result.reply(), result.cards());
  }

  // AI 가 못 고쳤을 때 (꺼짐 · 실패)
  public static DayPlanReviseResponse notMade() {
    return new DayPlanReviseResponse(false, null, null);
  }

  // 위기 표현이 감지됐을 때. AI 를 부르지 않습니다.
  public static DayPlanReviseResponse help() {
    return new DayPlanReviseResponse(true, null, null);
  }
}
