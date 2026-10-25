package com.ithopeschool.projectbe.domain.ai.dto;

import com.ithopeschool.projectbe.domain.ai.DayPlanRevise;
import java.util.List;

// "긴 계획(30일)" 의 결과입니다. 만들기 · 고치기 모두 이 모양입니다. 아무것도 저장하지 않습니다. (2026-10-18)
//   { "showHelp": false, "reply": "1~3단원을 개념 → 문제 순서로 나눴어.",
//     "plan": { …LongPlanOutline… },
//     "drafts": [ { "date": "2026-10-19", "startTime": "19:00", "durationMinutes": 60,
//                   "title": "미적분 1단원 개념 (1/2)", "checklist": ["교과서 12~20쪽 읽기"] } ],
//     "overflow": false }
// - plan    : 계획의 뼈대. 고칠 때 그대로 다시 보냅니다. null 이면 만들거나 고치지 못한 것입니다.
//             (AI 꺼짐 · 실패 · 공부 자료가 아닌 사진) 고치기였다면 FE 는 지금 계획을 그대로 둡니다.
// - drafts  : 뼈대를 코드가 날짜별로 나눈 카드 초안. 모양은 계획 핑퐁(/ai/day-plan/revise)과 같습니다.
//             학생이 [이대로 할게] 를 누르면 FE 가 카드로 저장합니다.
// - overflow: 할 일이 하루 공부 시간 안에 다 들어가지 않아 줄이거나 뺐으면 true. FE 가 "하루 시간을 늘릴까?" 를 물을 수 있습니다.
// - showHelp: 위기 표현이 감지됐으면 true. AI 를 부르지 않았거나 AI 답을 버렸고, plan · drafts 는 null 입니다.
public record LongPlanResponse(
    boolean showHelp,
    String reply,
    LongPlanOutline plan,
    List<DayPlanRevise.Card> drafts,
    boolean overflow) {

  // 만들었거나 고쳤을 때
  public static LongPlanResponse of(
      String reply, LongPlanOutline plan, List<DayPlanRevise.Card> drafts, boolean overflow) {
    return new LongPlanResponse(false, reply, plan, drafts, overflow);
  }

  // AI 가 못 만들었을 때 (꺼짐 · 실패 · 공부 자료가 아닌 사진)
  public static LongPlanResponse notMade() {
    return new LongPlanResponse(false, null, null, null, false);
  }

  // 위기 표현이 감지됐을 때
  public static LongPlanResponse help() {
    return new LongPlanResponse(true, null, null, null, false);
  }
}
