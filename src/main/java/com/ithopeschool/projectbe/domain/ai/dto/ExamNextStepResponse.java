package com.ithopeschool.projectbe.domain.ai.dto;

// 시험 회고 → 다음 카드에 넣을 한 가지의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #237 표 10번)
//   { "showHelp": false, "nextStep": { "text": "수학 기출 1회분 시간 재고 풀기", "subjectId": "3" } }
// - text     : 다음 카드에 넣을 행동 한 가지 (AI 가 씀, 40자까지)
// - subjectId: 그 행동을 넣을 과목 (코드가 고름: 예상보다 가장 아쉬운 과목). 실제 점수를 안 적었으면 null
// - nextStep 이 null 이면 AI 가 만들지 못한 것입니다. (AI 꺼짐 · 실패 · 쓸 수 없는 답)
//   에러가 아니라 200 이고, FE 는 기본값(회고 ③ 의 답 그대로)을 씁니다.
// - showHelp 가 true 면 회고 답에서 위기 표현이 감지된 것입니다. nextStep 은 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record ExamNextStepResponse(boolean showHelp, NextStep nextStep) {

  // 다음 카드에 넣을 한 가지
  public record NextStep(String text, String subjectId) {}

  // 만든 행동 (못 만들었으면 null)
  public static ExamNextStepResponse of(NextStep nextStep) {
    return new ExamNextStepResponse(false, nextStep);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static ExamNextStepResponse help() {
    return new ExamNextStepResponse(true, null);
  }
}
